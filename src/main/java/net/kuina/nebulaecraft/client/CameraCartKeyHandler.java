package net.kuina.nebulaecraft.client;

import net.kuina.nebulaecraft.NebulaecraftMod;
import net.kuina.nebulaecraft.entities.CameraCart;
import net.kuina.nebulaecraft.network.PacketCameraCartControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/**
 * 乘坐拍摄用车时复用 Railcraft 的机车按键（换向 / 加速 / 减速 / 模式）。
 *
 * <p>按键对象由 Railcraft 注册，这里只按描述名查找，不引用 Railcraft 的类；
 * 玩家在 Railcraft 分类下改键后同样生效。</p>
 */
public final class CameraCartKeyHandler {
    /** 下标即 {@link CameraCart.EntityCameraCart} 的 ACTION_* 值。 */
    private static final String[] DESCRIPTIONS = {
            "keybind.railcraft.loco.reverse",
            "keybind.railcraft.loco.faster",
            "keybind.railcraft.loco.slower",
            "keybind.railcraft.loco.mode"
    };

    // 必须放在 DESCRIPTIONS 之后：构造函数会读取它的长度，静态字段按声明顺序初始化。
    public static final CameraCartKeyHandler INSTANCE = new CameraCartKeyHandler();

    private final KeyBinding[] bindings = new KeyBinding[DESCRIPTIONS.length];
    private boolean resolved;

    private CameraCartKeyHandler() {
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState() || Keyboard.isRepeatEvent()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.currentScreen != null) {
            return;
        }
        if (!(player.getRidingEntity() instanceof CameraCart.EntityCameraCart)) {
            return;
        }

        resolveBindings(mc);
        int key = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
        // Railcraft 自己的处理器会在同一事件中消费 isPressed()，这里改为直接比对本次按键。
        for (int action = 0; action < bindings.length; action++) {
            KeyBinding binding = bindings[action];
            if (binding != null && binding.isActiveAndMatches(key)) {
                NebulaecraftMod.PACKET_HANDLER.sendToServer(new PacketCameraCartControl(action));
            }
        }
    }

    private void resolveBindings(Minecraft mc) {
        if (resolved) {
            return;
        }
        boolean all = true;
        for (int i = 0; i < DESCRIPTIONS.length; i++) {
            if (bindings[i] == null) {
                bindings[i] = find(mc, DESCRIPTIONS[i]);
            }
            all &= bindings[i] != null;
        }
        resolved = all;
    }

    private static KeyBinding find(Minecraft mc, String description) {
        for (KeyBinding binding : mc.gameSettings.keyBindings) {
            if (description.equals(binding.getKeyDescription())) {
                return binding;
            }
        }
        return null;
    }
}
