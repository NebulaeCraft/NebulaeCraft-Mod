package net.kuina.nebulaecraft.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.pipeline.VertexBufferConsumer;
import net.minecraftforge.client.model.pipeline.VertexTransformer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.util.List;

/** Writes only emissive faces directly to the chunk buffer, bypassing light/AO multiplication. */
public final class EmissiveBlockRenderer extends BlockModelRenderer {
    private final BlockModelRenderer delegate;
    private final ThreadLocal<VertexBufferConsumer> consumers = ThreadLocal.withInitial(VertexBufferConsumer::new);

    private EmissiveBlockRenderer(BlockModelRenderer delegate) {
        super(Minecraft.getMinecraft().getBlockColors());
        this.delegate = delegate;
    }

    public static void install() {
        BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
        BlockModelRenderer renderer = dispatcher.getBlockModelRenderer();
        if (!(renderer instanceof EmissiveBlockRenderer)) {
            // Forge exposes no renderer setter. The SRG name works in development and production.
            ObfuscationReflectionHelper.setPrivateValue(BlockRendererDispatcher.class, dispatcher,
                    new EmissiveBlockRenderer(renderer), "field_175027_c");
        }
    }

    @Override
    public boolean renderModel(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos,
                               BufferBuilder buffer, boolean checkSides) {
        return renderModel(world, model, state, pos, buffer, checkSides, MathHelper.getPositionRandom(pos));
    }

    @Override
    public boolean renderModel(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos,
                               BufferBuilder buffer, boolean checkSides, long rand) {
        if (!(model instanceof EmissiveModels.Model)) {
            return delegate.renderModel(world, model, state, pos, buffer, checkSides, rand);
        }
        EmissiveModels.Model emissiveModel = (EmissiveModels.Model) model;
        // Keep normal faces on the original renderer, including its AO and compatibility hooks.
        boolean rendered = delegate.renderModel(world, emissiveModel.getNormalModel(), state,
                pos, buffer, checkSides, rand);
        VertexBufferConsumer consumer = consumers.get();
        consumer.setBuffer(buffer);
        consumer.setOffset(pos);
        Vec3d offset = state.getOffset(world, pos);
        VertexTransformer shifted = new VertexTransformer(consumer) {
            private final float[] position = new float[4];

            @Override
            public void put(int element, float... data) {
                if (getVertexFormat().getElement(element).getUsage() == VertexFormatElement.EnumUsage.POSITION) {
                    position[0] = data[0] + (float) offset.x;
                    position[1] = data[1] + (float) offset.y;
                    position[2] = data[2] + (float) offset.z;
                    position[3] = data.length > 3 ? data[3] : 1F;
                    super.put(element, position);
                } else {
                    super.put(element, data);
                }
            }
        };
        List<BakedQuad> general = emissiveModel.getEmissiveQuads(state, null, rand);
        for (BakedQuad quad : general) {
            quad.pipe(shifted);
        }
        rendered |= !general.isEmpty();
        for (EnumFacing side : EnumFacing.values()) {
            List<BakedQuad> faces = emissiveModel.getEmissiveQuads(state, side, rand);
            if (!faces.isEmpty() && (!checkSides || state.shouldSideBeRendered(world, pos, side))) {
                for (BakedQuad quad : faces) {
                    quad.pipe(shifted);
                }
                rendered = true;
            }
        }
        return rendered;
    }
}
