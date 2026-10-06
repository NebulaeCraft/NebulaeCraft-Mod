package net.kuina.nebulaecraft.client.render;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.kuina.nebulaecraft.NebulaecraftMod;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.client.model.pipeline.VertexTransformer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.tuple.Pair;

import javax.vecmath.Matrix4f;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Uses the existing model faces as emitters, without OptiFine texture overlays. */
public final class EmissiveModels {
    public static final EmissiveModels INSTANCE = new EmissiveModels();

    // All 33 former _e textures. Model and block names are deliberately not hardcoded.
    private static final Set<String> TEXTURES = createTextureNames();
    // LightUtil normalizes SHORT attributes against 32767, so this packs to 240.
    private static final float FULL_BRIGHT = 240F / 32767F;
    private static final VertexFormat EMISSIVE_FORMAT = new VertexFormat(DefaultVertexFormats.BLOCK)
            .addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B);

    private EmissiveModels() {
    }

    private static Set<String> createTextureNames() {
        Set<String> names = new HashSet<>();
        for (String colour : Arrays.asList("white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
            names.add("nebulaecraft:blocks/lamp_" + colour + "_glow");
        }
        for (String name : Arrays.asList("exit", "signal_arrow_left", "signal_arrow_right", "signal_arrow_up",
                "signal_blue", "signal_green", "signal_red", "signal_white", "signal_yellow",
                "tdt_1", "tdt_2", "tdt_3", "trafficlight_left_green", "trafficlight_left_red",
                "trafficlight_ped_green", "trafficlight_ped_red", "trafficlight_righttext")) {
            names.add("nebulaecraft:blocks/" + name);
        }
        return Collections.unmodifiableSet(names);
    }

    private static boolean isEmissive(BakedQuad quad) {
        return TEXTURES.contains(quad.getSprite().getIconName());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onModelBake(ModelBakeEvent event) {
        // A fresh factory also drops all cached models/quads on every resource reload.
        Factory factory = new Factory();
        for (net.minecraft.client.renderer.block.model.ModelResourceLocation location
                : new ArrayList<>(event.getModelRegistry().getKeys())) {
            if (NebulaecraftMod.MODID.equals(location.getResourceDomain())) {
                IBakedModel model = event.getModelRegistry().getObject(location);
                event.getModelRegistry().putObject(location, factory.wrap(model));
            }
        }
    }

    private static BakedQuad makeEmissive(BakedQuad source) {
        UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(EMISSIVE_FORMAT);
        source.pipe(new VertexTransformer(builder) {
            @Override
            public void setQuadTint(int tint) {
                super.setQuadTint(-1);
            }

            @Override
            public void setApplyDiffuseLighting(boolean diffuse) {
                super.setApplyDiffuseLighting(false);
            }

            @Override
            public void put(int element, float... data) {
                VertexFormatElement attribute = getVertexFormat().getElement(element);
                if (attribute.getUsage() == VertexFormatElement.EnumUsage.COLOR) {
                    // FaceBakery may have already baked directional shading into RGB.
                    super.put(element, 1F, 1F, 1F, data.length > 3 ? data[3] : 1F);
                } else if (attribute.getUsage() == VertexFormatElement.EnumUsage.UV
                        && attribute.getIndex() == 1) {
                    super.put(element, FULL_BRIGHT, FULL_BRIGHT);
                } else {
                    super.put(element, data);
                }
            }
        });
        builder.setQuadTint(-1);
        builder.setApplyDiffuseLighting(false);
        return builder.build();
    }

    private static final class Factory {
        private final Map<IBakedModel, IBakedModel> models = new IdentityHashMap<>();

        synchronized IBakedModel wrap(IBakedModel model) {
            if (model == null || model instanceof Model) {
                return model;
            }
            IBakedModel cached = models.get(model);
            if (cached != null) {
                return cached;
            }
            boolean emits = hasEmissive(model.getQuads(null, null, 0));
            for (EnumFacing side : EnumFacing.values()) {
                emits |= hasEmissive(model.getQuads(null, side, 0));
            }
            IBakedModel result = emits ? new Model(model, this) : model;
            models.put(model, result);
            return result;
        }

        private boolean hasEmissive(List<BakedQuad> quads) {
            for (BakedQuad quad : quads) {
                if (isEmissive(quad)) {
                    return true;
                }
            }
            return false;
        }
    }

    private abstract static class DelegatingModel implements IBakedModel {
        final IBakedModel delegate;

        DelegatingModel(IBakedModel delegate) {
            this.delegate = delegate;
        }

        @Override public boolean isAmbientOcclusion() { return delegate.isAmbientOcclusion(); }
        @Override public boolean isAmbientOcclusion(IBlockState state) { return delegate.isAmbientOcclusion(state); }
        @Override public boolean isGui3d() { return delegate.isGui3d(); }
        @Override public boolean isBuiltInRenderer() { return delegate.isBuiltInRenderer(); }
        @Override public TextureAtlasSprite getParticleTexture() { return delegate.getParticleTexture(); }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return delegate.getItemCameraTransforms(); }
        @Override public ItemOverrideList getOverrides() { return delegate.getOverrides(); }
    }

    static final class Model extends DelegatingModel {
        // Identity keys avoid hashing large vertex lists; bounded caches also support dynamic models.
        private final Cache<List<BakedQuad>, QuadLists> quads = CacheBuilder.newBuilder()
                .weakKeys().maximumSize(128).build();
        private final Factory factory;
        private final ItemOverrideList overrides;
        private final IBakedModel normalModel;

        Model(IBakedModel delegate, Factory factory) {
            super(delegate);
            this.factory = factory;
            this.normalModel = new DelegatingModel(delegate) {
                @Override
                public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
                    return split(state, side, rand).normal;
                }
            };
            this.overrides = new ItemOverrideList(Collections.emptyList()) {
                @Override
                public ResourceLocation applyOverride(ItemStack stack, World world, EntityLivingBase entity) {
                    return delegate.getOverrides().applyOverride(stack, world, entity);
                }

                @Override
                public IBakedModel handleItemState(IBakedModel original, ItemStack stack,
                                                  World world, EntityLivingBase entity) {
                    return factory.wrap(delegate.getOverrides().handleItemState(delegate, stack, world, entity));
                }

                @Override
                public com.google.common.collect.ImmutableList<net.minecraft.client.renderer.block.model.ItemOverride>
                getOverrides() {
                    return delegate.getOverrides().getOverrides();
                }
            };
        }

        private QuadLists split(IBlockState state, EnumFacing side, long rand) {
            List<BakedQuad> source = delegate.getQuads(state, side, rand);
            QuadLists cached = quads.getIfPresent(source);
            if (cached == null) {
                cached = new QuadLists(source);
                quads.put(source, cached);
            }
            return cached;
        }

        @Override
        public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
            return split(state, side, rand).all;
        }

        List<BakedQuad> getEmissiveQuads(IBlockState state, EnumFacing side, long rand) {
            return split(state, side, rand).emissive;
        }

        IBakedModel getNormalModel() {
            return normalModel;
        }

        @Override public ItemOverrideList getOverrides() { return overrides; }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            Pair<? extends IBakedModel, Matrix4f> result = delegate.handlePerspective(type);
            return Pair.of(factory.wrap(result.getLeft()), result.getRight());
        }
    }

    private static final class QuadLists {
        final List<BakedQuad> all;
        final List<BakedQuad> normal;
        final List<BakedQuad> emissive;

        QuadLists(List<BakedQuad> source) {
            List<BakedQuad> all = new ArrayList<>(source.size());
            List<BakedQuad> normal = new ArrayList<>();
            List<BakedQuad> emissive = new ArrayList<>();
            for (BakedQuad quad : source) {
                if (isEmissive(quad)) {
                    BakedQuad lit = makeEmissive(quad);
                    all.add(lit);
                    emissive.add(lit);
                } else {
                    all.add(quad);
                    normal.add(quad);
                }
            }
            this.all = Collections.unmodifiableList(all);
            this.normal = Collections.unmodifiableList(normal);
            this.emissive = Collections.unmodifiableList(emissive);
        }
    }
}
