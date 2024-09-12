package com.drimoz.assembly.core.block;

import com.drimoz.assembly.Assembly;
import com.drimoz.assembly.core.tileentity.AssemblyTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class AssemblyTableBlock extends BaseEntityBlock {
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;

    private static final VoxelShape SHAPE =  Block.box(0, 0, 0, 16, 8, 16);

    public AssemblyTableBlock() {
        super(Properties.copy(Blocks.IRON_BLOCK));
    }

    // Shape

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    public BlockState updateShape(BlockState stateIn, Direction facing, BlockState facingState, LevelAccessor worldIn, BlockPos currentPos, BlockPos facingPos) {
        return super.updateShape(stateIn, facing, facingState, worldIn, currentPos, facingPos);
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    // Block State

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(ENABLED, Boolean.TRUE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(ENABLED);
    }

    @Override
    public void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
        if (!pOldState.is(pState.getBlock())) {
            this.checkPoweredState(pLevel, pPos, pState);
        }
    }

    // Enabled

    @Override
    public void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock, BlockPos pFromPos, boolean pIsMoving) {
        this.checkPoweredState(pLevel, pPos, pState);
    }

    private void checkPoweredState(Level pLevel, BlockPos pPos, BlockState pState) {
        boolean flag = !pLevel.hasNeighborSignal(pPos);
        if (flag != pState.getValue(ENABLED)) {
            pLevel.setBlock(pPos, pState.setValue(ENABLED, Boolean.valueOf(flag)), 5);
        }
    }

    // Interactions

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (pState.getBlock() != pNewState.getBlock()) {
            BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
            if (blockEntity instanceof AssemblyTableBlockEntity) {
                ((AssemblyTableBlockEntity) blockEntity).drops();
                pLevel.updateNeighbourForOutputSignal(pPos, this);
            }
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {

        if (pLevel.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity blockEntity = pLevel.getBlockEntity(pPos);

        if(blockEntity instanceof AssemblyTableBlockEntity) {
            NetworkHooks.openScreen(((ServerPlayer)pPlayer), (AssemblyTableBlockEntity)blockEntity, pPos);
        } else {
            throw new IllegalStateException("Missing Container Provider for AssemblyTableBlockEntity");
        }

        return InteractionResult.SUCCESS;
    }

    // BlockEntity / MenuProvider

    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new AssemblyTableBlockEntity(pPos, pState);
    }
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : createTicker(level, blockEntityType, Assembly.ASSEMBLY_TABLE_ENTITY.get());
    }

    public MenuProvider getMenuProvider(BlockState pBlockState, Level pLevel, BlockPos pPos) {
        BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
        return blockEntity instanceof MenuProvider ? (MenuProvider)blockEntity : null;
    }

    @Nullable
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(BlockEntityType<A> eTypeA, BlockEntityType<E> eTypeE, BlockEntityTicker<? super E> eTickerE) {
        return eTypeE == eTypeA ? (BlockEntityTicker<A>) eTickerE : null;
    }

    @Nullable
    protected static <T extends BlockEntity> BlockEntityTicker<T> createTicker(Level pLevel, BlockEntityType<T> eTypeT, BlockEntityType<? extends AssemblyTableBlockEntity> eTypeI) {
        return pLevel.isClientSide ? null : createTickerHelper(eTypeT, eTypeI, AssemblyTableBlockEntity::tick);
    }
}
