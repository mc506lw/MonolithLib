package top.mc506lw.monolith.internal.mixin

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import top.mc506lw.monolith.internal.listener.MonolithBlockListener

@Mixin(Level::class)
abstract class LevelMixin {
    
    @Inject(
        method = ["markAndNotifyBlock"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;onPlace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V")],
        cancellable = false
    )
    private fun onMarkAndNotifyBlock(
        pos: BlockPos,
        state: BlockState,
        oldState: BlockState,
        flags: Int,
        priority: Int,
        ci: CallbackInfo
    ) {
        try {
            val level = this as Level
            
            if (!level.isClientSide) {
                MonolithBlockListener.getInstance().onBlockChangeNative(
                    pos.x, pos.y, pos.z,
                    level.dimension().location().toString()
                )
            }
        } catch (e: Exception) {
            
        }
    }
}
