package com.simibubi.create.content.kinetics.saw;

import java.util.Optional;

import com.jozufozu.flywheel.core.virtual.VirtualRenderWorld;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.foundation.utility.AbstractBlockBreakQueue;
import com.simibubi.create.foundation.utility.TreeCutter;
import com.simibubi.create.foundation.utility.VecHelper;

import io.github.fabricators_of_create.porting_lib.transfer.TransferUtil;
import io.github.fabricators_of_create.porting_lib.transfer.item.ItemHandlerHelper;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SawMovementBehaviour extends BlockBreakingMovementBehaviour {

	@Override
	public boolean isActive(MovementContext context) {
		return super.isActive(context)
			&& !VecHelper.isVecPointingTowards(context.relativeMotion, context.state.getValue(SawBlock.FACING)
				.getOpposite());
	}

	@Override
	public Vec3 getActiveAreaOffset(MovementContext context) {
		return Vec3.atLowerCornerOf(context.state.getValue(SawBlock.FACING)
			.getNormal())
			.scale(.65f);
	}

	@Override
	public void visitNewPosition(MovementContext context, BlockPos pos) {
		super.visitNewPosition(context, pos);
		Vec3 facingVec = Vec3.atLowerCornerOf(context.state.getValue(SawBlock.FACING)
			.getNormal());
		facingVec = context.rotation.apply(facingVec);

		Direction closestToFacing = Direction.getNearest(facingVec.x, facingVec.y, facingVec.z);
		if (closestToFacing.getAxis()
			.isVertical() && context.data.contains("BreakingPos")) {
			context.data.remove("BreakingPos");
			context.stall = false;
		}
	}

	@Override
	public boolean canBreak(Level world, BlockPos breakingPos, BlockState state) {
		return super.canBreak(world, breakingPos, state) && SawBlockEntity.isSawable(state);
	}

	@Override
	protected void onBlockBroken(MovementContext context, BlockPos pos, BlockState brokenState) {
		if (brokenState.is(BlockTags.LEAVES))
			return;

		Optional<AbstractBlockBreakQueue> dynamicTree = TreeCutter.findDynamicTree(brokenState.getBlock(), pos);
		if (dynamicTree.isPresent()) {
			dynamicTree.get()
				.destroyBlocks(context.world, null, (stack, dropPos) -> dropItemFromCutTree(context, stack, dropPos));
			return;
		}

		TreeCutter.findTree(context.world, pos)
			.destroyBlocks(context.world, null, (stack, dropPos) -> dropItemFromCutTree(context, stack, dropPos));
	}

	public void dropItemFromCutTree(MovementContext context, BlockPos pos, ItemStack stack) {
		long inserted = TransferUtil.insertItem(context.contraption.getSharedInventory(), stack);
		if (inserted == stack.getCount())
			return;
		long remaining = stack.getCount() - inserted;
		ItemStack remainder = stack.copy();
		remainder.setCount((int) remaining);

		Level world = context.world;
		Vec3 dropPos = VecHelper.getCenterOf(pos);
		float distance = context.position == null ? 1 : (float) dropPos.distanceTo(context.position);
		ItemEntity entity = new ItemEntity(world, dropPos.x, dropPos.y, dropPos.z, remainder);
		entity.setDeltaMovement(context.relativeMotion.scale(distance / 20f));
		world.addFreshEntity(entity);
	}

	@Override
	@Environment(value = EnvType.CLIENT)
	public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
		ContraptionMatrices matrices, MultiBufferSource buffer) {
		SawRenderer.renderInContraption(context, renderWorld, matrices, buffer);
	}

	@Override
	protected boolean shouldDestroyStartBlock(BlockState stateToBreak) {
		return !TreeCutter.canDynamicTreeCutFrom(stateToBreak.getBlock());
	}

	@Override
	protected DamageSource getDamageSource() {
		return SawBlock.damageSourceSaw;
	}
}
