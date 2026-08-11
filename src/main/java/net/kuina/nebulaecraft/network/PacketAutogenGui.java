package net.kuina.nebulaecraft.network;

import io.netty.buffer.ByteBuf;
import net.kuina.nebulaecraft.NebulaecraftMod;
import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.kuina.nebulaecraft.autogen.TunnelGenerationManager;
import net.kuina.nebulaecraft.autogen.template.AutogenParameter;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplate;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplateRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server snapshot used to open the autogen wand screen. */
public final class PacketAutogenGui implements IMessage {
    private static final int MAX_TEMPLATES = 64;
    private static final int MAX_PARAMETERS = 32;
    private static final int MAX_CHOICES = 64;
    private static final int MAX_STRING = 256;

    public boolean hasStart;
    public boolean hasEnd;
    public String start = "";
    public String end = "";
    public String status = "";
    public boolean hasPreview;
    public boolean active;
    public boolean canCancelActive;
    public boolean canUndo;
    public List<TemplateData> templates = Collections.emptyList();

    public PacketAutogenGui() {
    }

    public static PacketAutogenGui create(EntityPlayerMP player) {
        PacketAutogenGui packet = new PacketAutogenGui();
        AutogenSelection.Selection selection = AutogenSelection.get(player);
        packet.hasStart = selection.start != null;
        packet.hasEnd = selection.end != null;
        packet.start = anchorText(selection.start);
        packet.end = anchorText(selection.end);

        TunnelGenerationManager manager = TunnelGenerationManager.INSTANCE;
        packet.status = manager.status(player);
        packet.hasPreview = manager.hasPreview(player);
        packet.active = manager.hasActiveJob();
        packet.canCancelActive = manager.canCancelActiveJob(player);
        packet.canUndo = manager.canUndo(player);

        List<TemplateData> templateData = new ArrayList<>();
        for (AutogenTemplate template : AutogenTemplateRegistry.getAll()) {
            templateData.add(new TemplateData(template));
        }
        packet.templates = Collections.unmodifiableList(templateData);
        return packet;
    }

    private static String anchorText(AutogenSelection.Anchor anchor) {
        if (anchor == null) {
            return "";
        }
        return "D" + anchor.dimension + "  " + anchor.pos.getX() + " "
                + anchor.pos.getY() + " " + anchor.pos.getZ() + "  " + anchor.facing.getName();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hasStart = buf.readBoolean();
        hasEnd = buf.readBoolean();
        start = readString(buf);
        end = readString(buf);
        status = readString(buf);
        hasPreview = buf.readBoolean();
        active = buf.readBoolean();
        canCancelActive = buf.readBoolean();
        canUndo = buf.readBoolean();
        int count = checkedCount(buf.readInt(), MAX_TEMPLATES, "template");
        List<TemplateData> decoded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            decoded.add(TemplateData.read(buf));
        }
        templates = Collections.unmodifiableList(decoded);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(hasStart);
        buf.writeBoolean(hasEnd);
        writeString(buf, start);
        writeString(buf, end);
        writeString(buf, status);
        buf.writeBoolean(hasPreview);
        buf.writeBoolean(active);
        buf.writeBoolean(canCancelActive);
        buf.writeBoolean(canUndo);
        buf.writeInt(templates.size());
        for (TemplateData template : templates) {
            template.write(buf);
        }
    }

    private static int checkedCount(int count, int maximum, String name) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid autogen " + name + " count: " + count);
        }
        return count;
    }

    private static String readString(ByteBuf buf) {
        String value = ByteBufUtils.readUTF8String(buf);
        if (value.length() > MAX_STRING) {
            throw new IllegalArgumentException("Autogen GUI string is too long");
        }
        return value;
    }

    private static void writeString(ByteBuf buf, String value) {
        ByteBufUtils.writeUTF8String(buf, value == null ? "" : value);
    }

    public static final class TemplateData {
        public final String id;
        public final String displayName;
        public final List<ParameterData> parameters;

        private TemplateData(String id, String displayName, List<ParameterData> parameters) {
            this.id = id;
            this.displayName = displayName;
            this.parameters = Collections.unmodifiableList(parameters);
        }

        TemplateData(AutogenTemplate template) {
            this.id = template.getId();
            this.displayName = template.getDisplayName();
            List<ParameterData> result = new ArrayList<>();
            for (AutogenParameter parameter : template.getParameters()) {
                result.add(new ParameterData(parameter));
            }
            this.parameters = Collections.unmodifiableList(result);
        }

        static TemplateData read(ByteBuf buf) {
            String id = readString(buf);
            String displayName = readString(buf);
            int count = checkedCount(buf.readInt(), MAX_PARAMETERS, "parameter");
            List<ParameterData> parameters = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                parameters.add(ParameterData.read(buf));
            }
            return new TemplateData(id, displayName, parameters);
        }

        void write(ByteBuf buf) {
            writeString(buf, id);
            writeString(buf, displayName);
            buf.writeInt(parameters.size());
            for (ParameterData parameter : parameters) {
                parameter.write(buf);
            }
        }
    }

    public static final class ParameterData {
        public final String id;
        public final String labelKey;
        public final AutogenParameter.Type type;
        public final String defaultValue;
        public final List<String> choices;
        public final String trueArgument;

        private ParameterData(String id, String labelKey, AutogenParameter.Type type,
                              String defaultValue, List<String> choices, String trueArgument) {
            this.id = id;
            this.labelKey = labelKey;
            this.type = type;
            this.defaultValue = defaultValue;
            this.choices = Collections.unmodifiableList(choices);
            this.trueArgument = trueArgument;
        }

        ParameterData(AutogenParameter parameter) {
            this(parameter.getId(), parameter.getLabelKey(), parameter.getType(),
                    parameter.getDefaultValue(), new ArrayList<>(parameter.getChoices()),
                    parameter.getTrueArgument());
        }

        static ParameterData read(ByteBuf buf) {
            String id = readString(buf);
            String labelKey = readString(buf);
            int ordinal = buf.readUnsignedByte();
            if (ordinal >= AutogenParameter.Type.values().length) {
                throw new IllegalArgumentException("Invalid autogen parameter type: " + ordinal);
            }
            String defaultValue = readString(buf);
            String trueArgument = readString(buf);
            int count = checkedCount(buf.readInt(), MAX_CHOICES, "choice");
            List<String> choices = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                choices.add(readString(buf));
            }
            return new ParameterData(id, labelKey, AutogenParameter.Type.values()[ordinal],
                    defaultValue, choices, trueArgument);
        }

        void write(ByteBuf buf) {
            writeString(buf, id);
            writeString(buf, labelKey);
            buf.writeByte(type.ordinal());
            writeString(buf, defaultValue);
            writeString(buf, trueArgument);
            buf.writeInt(choices.size());
            for (String choice : choices) {
                writeString(buf, choice);
            }
        }
    }

    public static final class Handler implements IMessageHandler<PacketAutogenGui, IMessage> {
        @Override
        public IMessage onMessage(PacketAutogenGui message, MessageContext context) {
            NebulaecraftMod.proxy.handleAutogenGui(message);
            return null;
        }
    }
}
