package me.andrew.healthindicators.mixin;

import me.andrew.healthindicators.Config;
import me.andrew.healthindicators.HeartType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.WeakHashMap;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends EntityRenderer<T, S> implements FeatureRendererContext<S, M> {

    @Unique @SuppressWarnings("ALL") WeakHashMap<LivingEntityRenderState, LivingEntity> entities = new WeakHashMap<>();

    public PlayerEntityRendererMixin(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    public void updateRenderStateInject(T livingEntity, S livingEntityRenderState, float f, CallbackInfo ci) {
        entities.put(livingEntityRenderState, livingEntity);
    }


    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN")
    )
    @SuppressWarnings("unchecked")
    public void renderHealth(S livingEntityRenderState, MatrixStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue, CameraRenderState cameraRenderState, CallbackInfo ci) {

        LivingEntity livingEntity = entities.get(livingEntityRenderState);

        if (!(livingEntity instanceof AbstractClientPlayerEntity abstractClientPlayerEntity)) return;

        if (!Config.getRenderingEnabled()) return;

        if (!shouldRenderHeartsForEntity(abstractClientPlayerEntity)) return;

        matrixStack.push();

        double d = this.dispatcher.getSquaredDistanceToCamera(abstractClientPlayerEntity);

        matrixStack.translate(0, abstractClientPlayerEntity.getHeight() + 0.5f, 0);

        T TEntity = (T) abstractClientPlayerEntity;

        if (this.hasLabel(TEntity, d) && d <= 4096.0) {
            matrixStack.translate(0.0D, 9.0F * 1.15F * 0.025F, 0.0D);
            if (d < 100.0 && abstractClientPlayerEntity.getEntityWorld().getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME) != null) {
                matrixStack.translate(0.0D, 9.0F * 1.15F * 0.025F, 0.0D);
            }
        }

        if (this.dispatcher.camera == null) return;
        matrixStack.multiply(this.dispatcher.camera.getRotation());
        matrixStack.scale(-1, 1, 1);

        float pixelSize = 0.025F;
        matrixStack.scale(pixelSize, pixelSize, pixelSize);
        matrixStack.translate(0, Config.getHeartOffset(), 0);

        AtlasManager guiAtlasManager = MinecraftClient.getInstance().getAtlasManager();

        int healthRed = MathHelper.ceil(abstractClientPlayerEntity.getHealth());
        int maxHealth = MathHelper.ceil(abstractClientPlayerEntity.getMaxHealth());
        int healthYellow = MathHelper.ceil(abstractClientPlayerEntity.getAbsorptionAmount());

        int heartsRed = MathHelper.ceil(healthRed / 2.0f);
        boolean lastRedHalf = (healthRed & 1) == 1;
        int heartsNormal = MathHelper.ceil(maxHealth / 2.0f);
        int heartsYellow = MathHelper.ceil(healthYellow / 2.0f);
        boolean lastYellowHalf = (healthYellow & 1) == 1;
        int heartsTotal = heartsNormal + heartsYellow;

        int heartsPerRow = Config.getHeartStackingEnabled() ? 10 : heartsTotal;
        int rowsTotal = (heartsTotal + heartsPerRow - 1) / heartsPerRow;
        int rowOffset = Math.max(10 - (rowsTotal - 2), 3);

        int pixelsTotal = Math.min(heartsTotal, heartsPerRow) * 8 + 1;
        float maxX = pixelsTotal / 2.0f;
        for (int heart = 0; heart < heartsTotal; heart++){
            int row = heart / heartsPerRow;
            int col = heart % heartsPerRow;

            float x = maxX - col * 8;
            float y = row * rowOffset;
            float z = row * 0.01F;
            drawHeart(matrixStack, x, y, z, HeartType.EMPTY, guiAtlasManager);

            HeartType type;
            if (heart < heartsRed) {
                type = HeartType.RED_FULL;
                if (heart == heartsRed - 1 && lastRedHalf) {
                    type = HeartType.RED_HALF;
                }
            } else if (heart < heartsNormal) {
                type = HeartType.EMPTY;
            } else {
                type = HeartType.YELLOW_FULL;
                if (heart == heartsTotal - 1 && lastYellowHalf) {
                    type = HeartType.YELLOW_HALF;
                }
            }
            if (type != HeartType.EMPTY) {
                drawHeart(matrixStack, x, y, z, type, guiAtlasManager);
            }
        }
        matrixStack.pop();
    }

    @Unique
    private static boolean shouldRenderHeartsForEntity(Entity entity) {
        if (entity instanceof AbstractClientPlayerEntity abstractClientPlayerEntity) {
            return !abstractClientPlayerEntity.isMainPlayer() && hasInvisibilityRequirements(abstractClientPlayerEntity);
        }

        return false;
    }

    @Unique
    private static boolean hasInvisibilityRequirements(PlayerEntity entity) {
        List<EquipmentSlot> armorSlots = List.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        );
        if (entity.isInvisible()) {
            for (EquipmentSlot eSlot : armorSlots) {
                ItemStack stack = entity.getEquippedStack(eSlot);
                if (stack.contains(DataComponentTypes.EQUIPPABLE)) return true;
            }
            return false;
        }
        return true;
    }

    @Unique
    private static void drawHeart(
            MatrixStack matrices,
            float x, float y, float z,
            HeartType type,
            AtlasManager guiAtlasManager     // = MinecraftClient.getInstance().getGuiAtlasManager()
    ) {
        Sprite sprite = guiAtlasManager.getAtlasTexture(Atlases.GUI).getSprite(type.texture);

        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();

        float heartSize = 9.0f;

        matrices.push();

        Matrix4f model = matrices.peek().getPositionMatrix();

        Identifier atlasTexture = Identifier.ofVanilla("textures/atlas/gui.png");
        VertexConsumer vertexConsumer = sprite.getTextureSpecificVertexConsumer(
                MinecraftClient.getInstance()
                        .getBufferBuilders()
                        .getEntityVertexConsumers()
                        .getBuffer(RenderLayers.entityCutoutNoCull(atlasTexture))
        );

        drawVertex(model, vertexConsumer, x, y - heartSize, z, minU, maxV);
        drawVertex(model, vertexConsumer, x - heartSize, y - heartSize, z, maxU, maxV);
        drawVertex(model, vertexConsumer, x - heartSize, y, z, maxU, minV);
        drawVertex(model, vertexConsumer, x, y, z, minU, minV);

        matrices.pop();
    }

    @Unique
    private static void drawVertex(Matrix4f model, VertexConsumer vertices, float x, float y, float z, float u, float v) {
        vertices.vertex(model, x, y, z).texture(u, v).color(255, 255, 255, 255).light(15728880).overlay(OverlayTexture.DEFAULT_UV).normal(0, 1, 0);
    }
}
