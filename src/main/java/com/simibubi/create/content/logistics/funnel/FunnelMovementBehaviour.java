package com.simibubi.create.content.logistics.funnel;

import java.util.List;

import com.simibubi.create.content.contraptions.behaviour.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.item.ItemHelper;

import io.github.fabricators_of_create.porting_lib.transfer.TransferUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class FunnelMovementBehaviour implements MovementBehaviour {

	private final boolean hasFilter;

	public static FunnelMovementBehaviour andesite() {
		return new FunnelMovementBehaviour(false);
	}

	public static FunnelMovementBehaviour brass() {
		return new FunnelMovementBehaviour(true);
	}

	private FunnelMovementBehaviour(boolean hasFilter) {
		this.hasFilter = hasFilter;
	}

	@Override
	public Vec3 getActiveAreaOffset(MovementContext context) {
		Direction facing = FunnelBlock.getFunnelFacing(context.state);
		Vec3 vec = Vec3.atLowerCornerOf(facing.getNormal());
		if (facing != Direction.UP)
			return vec.scale(context.state.getValue(FunnelBlock.EXTRACTING) ? .15 : .65);

		return vec.scale(.65);
	}

	@Override
	public void visitNewPosition(MovementContext context, BlockPos pos) {
		MovementBehaviour.super.visitNewPosition(context, pos);

		if (context.state.getValue(FunnelBlock.EXTRACTING))
			extract(context, pos);
		else
			succ(context, pos);

	}

	private void extract(MovementContext context, BlockPos pos) {
		Level world = context.world;

		Vec3 entityPos = context.position;
		if (context.state.getValue(FunnelBlock.FACING) != Direction.DOWN)
			entityPos = entityPos.add(0, -.5f, 0);

		if (!world.getBlockState(pos)
			.getCollisionShape(world, pos)
			.isEmpty())
			return;

		if (!world.getEntitiesOfClass(ItemEntity.class, new AABB(new BlockPos(entityPos)))
			.isEmpty())
			return;

		FilterItemStack filter = context.getFilterFromBE();
		int filterAmount = context.blockEntityData.getInt("FilterAmount");
		boolean upTo = context.blockEntityData.getBoolean("UpTo");
		if (filterAmount <= 0)
			filterAmount = hasFilter ? 64 : 1;

		ItemStack extract = ItemHelper.extract(context.contraption.getSharedInventory(),
			s -> filter.test(world, s),
			upTo ? ItemHelper.ExtractionCountMode.UPTO : ItemHelper.ExtractionCountMode.EXACTLY, filterAmount, false);

		if (extract.isEmpty())
			return;

		if (world.isClientSide)
			return;

		ItemEntity entity = new ItemEntity(world, entityPos.x, entityPos.y, entityPos.z, extract);
		entity.setDeltaMovement(Vec3.ZERO);
		entity.setPickUpDelay(5);
		world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1 / 16f, .1f);
		world.addFreshEntity(entity);
	}

	private void succ(MovementContext context, BlockPos pos) {
		Level world = context.world;
		List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class, new AABB(pos));
		FilterItemStack filter = context.getFilterFromBE();

		try (Transaction t = TransferUtil.getTransaction()) {
			for (ItemEntity item : items) {
				if (!item.isAlive())
					continue;
				ItemStack toInsert = item.getItem();
				if (toInsert.isEmpty() || (!filter.test(context.world, toInsert)))
					continue;
				long inserted = TransferUtil.insertItem(context.contraption.getSharedInventory(), toInsert);
				if (inserted == 0)
					continue;
				if (inserted == toInsert.getCount()) {
					item.setItem(ItemStack.EMPTY);
					item.discard();
					continue;
				}
				ItemStack remainder = item.getItem().copy();
				remainder.shrink(ItemHelper.truncateLong(inserted));
				item.setItem(remainder);
			}
			t.commit();
		}
	}

	@Override
	public boolean renderAsNormalBlockEntity() {
		return true;
	}

}
