package net.kuina.nebulaecraft.gui;

import net.kuina.nebulaecraft.NebulaecraftMod;
import net.kuina.nebulaecraft.network.PacketRoadmarkText;
import net.kuina.nebulaecraft.tileentity.TileEntityRoadmarkText;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class GuiRoadmarkText extends GuiScreen {
    private GuiTextField textField;
    private final TileEntityRoadmarkText te;
    private int selectedColor;

    public GuiRoadmarkText(TileEntityRoadmarkText te) {
        this.te = te;
        this.selectedColor = te.getColor();
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        // 在屏幕中央创建一个输入框
        this.textField = new GuiTextField(0, this.fontRenderer, this.width / 2 - 100, this.height / 2 - 10, 200, 20);
        this.textField.setMaxStringLength(20);
        this.textField.setFocused(true);
        this.textField.setText(te.getText());
        this.buttonList.add(new GuiButton(0, this.width / 2 - 50, this.height / 2 + 25, 100, 20, "保存"));
        this.buttonList.add(new GuiButton(1, this.width / 2 + 55, this.height / 2 + 25, 80, 20, getColorButtonText()));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            // 关闭界面，这会自动触发下面的 onGuiClosed() 方法，从而发送数据包
            this.mc.displayGuiScreen(null);
        } else if (button.id == 1) {
            this.selectedColor = this.selectedColor == TileEntityRoadmarkText.COLOR_YELLOW
                    ? TileEntityRoadmarkText.COLOR_WHITE
                    : TileEntityRoadmarkText.COLOR_YELLOW;
            button.displayString = getColorButtonText();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        // 关闭界面时，将内容发送给服务端
        NebulaecraftMod.PACKET_HANDLER.sendToServer(new PacketRoadmarkText(te.getPos(), this.textField.getText(), this.selectedColor));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        this.textField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null); // 回车或ESC退出并保存
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer, "请输入路面文字", this.width / 2, this.height / 2 - 30, 0xFFFFFF);
        this.textField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String getColorButtonText() {
        return this.selectedColor == TileEntityRoadmarkText.COLOR_YELLOW ? "颜色: 黄" : "颜色: 白";
    }
}
