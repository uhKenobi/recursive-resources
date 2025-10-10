package nl.enjarai.recursiveresources.mixin.skip_spam_log;

import com.mojang.serialization.DataResult;
import net.minecraft.resource.AbstractFileResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(AbstractFileResourcePack.class)
public abstract class AbstractFileResourcePackMixin {
    @Redirect(
            method = "parseMetadata(Lnet/minecraft/resource/metadata/ResourceMetadataSerializer;Ljava/io/InputStream;Lnet/minecraft/resource/ResourcePackInfo;)Ljava/lang/Object;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DataResult;ifError(Ljava/util/function/Consumer;)Lcom/mojang/serialization/DataResult;"
            )
    )
    private static <R> DataResult<R> recursiveresources$skipUnnessecaryLogging(DataResult<R> instance, Consumer<? super DataResult.Error<R>> consumer) {
        // Don't log anything
        return instance;
    }
}
