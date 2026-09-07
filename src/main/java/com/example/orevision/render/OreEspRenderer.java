package com.example.orevision.render;

import com.example.orevision.OreScanner;
import com.example.orevision.OreVisionClient;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Renders a colored box for every cached ore position, through walls.
 *
 * This follows the "extraction then drawing" pattern documented at
 * https://docs.fabricmc.net/develop/rendering/world for Minecraft 26.2's
 * split-phase render pipeline: a custom RenderPipeline based on the vanilla
 * debug filled-box pipeline but with depth testing disabled, so boxes draw
 * through terrain.
 *
 * VERIFY: this whole class is adapted from Fabric's own worked example
 * (a single waypoint) generalized to many boxes at once. The single most
 * likely thing to need tuning is BUFFER_SIZE_BYTES below - if boxes stop
 * rendering or you see visual corruption once ore counts get high, increase
 * it. Everything else (pipeline definition, vertex layout, event names)
 * follows the documented pattern directly.
 */
public class OreEspRenderer {

    // Each box is 6 faces * 4 vertices. Tune this if you scan a huge radius
    // and start seeing dropped/corrupted boxes - it's a rough sizing, not
    // a load-bearing exact calculation.
    private static final int MAX_BOXES_HINT = 2048;
    private static final int BUFFER_SIZE_BYTES = RenderType.SMALL_BUFFER_SIZE * 32;

    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(OreVisionClient.MOD_ID, "pipeline/ore_esp_through_walls"))
                    .withDepthStencilState(Optional.empty())
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer STAGED_BUFFER =
            new StagedVertexBuffer(() -> "Ore Vision ESP Buffer", BUFFER_SIZE_BYTES);

    // Render state captured during extraction; must be immutable/thread-safe
    // per the extraction/drawing split contract.
    private record OreBox(int x, int y, int z, float r, float g, float b, float a) { }

    private static volatile List<OreBox> extractedBoxes = List.of();

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(OreEspRenderer::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(OreEspRenderer::drawAll);
    }

    private static void extract(LevelExtractionContext context) {
        if (!OreVisionClient.STATE.espEnabled) {
            extractedBoxes = List.of();
            return;
        }

        Map<BlockPos, Integer> ores = OreScanner.getFoundOres();
        if (ores.isEmpty()) {
            extractedBoxes = List.of();
            return;
        }

        List<OreBox> boxes = new ArrayList<>(Math.min(ores.size(), MAX_BOXES_HINT));
        int count = 0;
        for (Map.Entry<BlockPos, Integer> entry : ores.entrySet()) {
            if (count++ >= MAX_BOXES_HINT) break; // guard against buffer overflow

            BlockPos pos = entry.getKey();
            int argb = entry.getValue();
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >> 8) & 0xFF) / 255f;
            float b = (argb & 0xFF) / 255f;

            boxes.add(new OreBox(pos.getX(), pos.getY(), pos.getZ(), r, g, b, 0.55f));
        }
        extractedBoxes = boxes;
    }

    private static void drawAll(LevelRenderContext context) {
        List<OreBox> boxes = extractedBoxes;
        if (boxes.isEmpty()) {
            return;
        }

        RenderPipeline pipeline = FILLED_THROUGH_WALLS;
        VertexFormat formatBinding = pipeline.getVertexFormatBinding(0);
        if (formatBinding == null) {
            return;
        }

        PrimitiveTopology primitive = pipeline.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw = STAGED_BUFFER.appendDraw(
                formatBinding,
                primitive,
                primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null
        );

        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        Matrix4fc positionMatrix = matrices.last().pose();
        VertexConsumer builder = STAGED_BUFFER.getVertexBuilder(draw);

        for (OreBox box : boxes) {
            renderFilledBox(positionMatrix, builder,
                    box.x(), box.y(), box.z(),
                    box.x() + 1f, box.y() + 1f, box.z() + 1f,
                    box.r(), box.g(), box.b(), box.a());
        }

        matrices.popPose();

        STAGED_BUFFER.upload();
        StagedVertexBuffer.ExecuteInfo info = STAGED_BUFFER.getExecuteInfo(draw);
        if (info != null) {
            drawExecuted(Minecraft.getInstance(), info, pipeline);
        }
        STAGED_BUFFER.endFrame();
    }

    private static void renderFilledBox(Matrix4fc positionMatrix, VertexConsumer buffer,
                                         float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ,
                                         float red, float green, float blue, float alpha) {
        // Front face
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);

        // Back face
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        // Left face
        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

        // Right face
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

        // Top face
        buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

        // Bottom face
        buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
    }

    private static void drawExecuted(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
        GpuTextureView colorTexture = mainTarget.getColorTextureView();
        if (colorTexture == null) {
            return;
        }

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> OreVisionClient.MOD_ID + " ore esp", colorTexture, Optional.empty(),
                        mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
            renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
            renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    /** Called from GameRendererMixin on GameRenderer#close. */
    public static void close() {
        STAGED_BUFFER.close();
    }
}
