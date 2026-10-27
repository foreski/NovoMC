package com.novomc.client.modules;

import com.novomc.client.module.Module;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.lwjgl.Sys;

/**
 * Screenshot manager: after F2, the chat message gets [Open] [Copy] [Delete]
 * buttons (run through the client-only /novoscreenshot command), and
 * GuiScreenshots shows a gallery of the folder.
 */
public class ScreenshotsModule extends Module {
    private static volatile boolean active;

    public ScreenshotsModule() {
        super("screenshots");
    }

    @Override
    protected void onEnable() {
        active = true;
    }

    @Override
    protected void onDisable() {
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static File dir() {
        return new File(Minecraft.getMinecraft().mcDataDir, "screenshots");
    }

    /** PNG screenshots, newest first. */
    public static List<File> list() {
        File[] files = dir().listFiles();
        if (files == null) {
            return new ArrayList<File>();
        }
        List<File> out = new ArrayList<File>();
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) {
                out.add(f);
            }
        }
        Collections.sort(out, new Comparator<File>() {
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return out;
    }

    /** A screenshot by bare file name; null for anything outside the screenshots folder. */
    public static File byName(String name) {
        if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return null;
        }
        File f = new File(dir(), name);
        return f.isFile() ? f : null;
    }

    public static void open(File f) {
        Sys.openURL("file:///" + f.getAbsolutePath().replace('\\', '/'));
    }

    /** Puts the image on the clipboard; false if it couldn't. */
    public static boolean copy(File f) {
        try {
            final Image image = ImageIO.read(f);
            if (image == null) {
                return false;
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] {DataFlavor.imageFlavor};
                }

                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return DataFlavor.imageFlavor.equals(flavor);
                }

                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(flavor)) {
                        throw new UnsupportedFlavorException(flavor);
                    }
                    return image;
                }
            }, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Vanilla's "Saved screenshot as ..." plus [Open] [Copy] [Delete]. Called from MixinScreenShotHelper. */
    public static IChatComponent decorate(IChatComponent vanilla) {
        List<File> files = list();
        if (!active || files.isEmpty() || System.currentTimeMillis() - files.get(0).lastModified() > 5000) {
            return vanilla;
        }
        String name = files.get(0).getName();
        IChatComponent out = new ChatComponentText("").appendSibling(vanilla);
        out.appendSibling(action(" [Open]", EnumChatFormatting.AQUA, "open", name, "Open the screenshot"));
        out.appendSibling(action(" [Copy]", EnumChatFormatting.GREEN, "copy", name, "Copy the image to the clipboard"));
        out.appendSibling(action(" [Delete]", EnumChatFormatting.RED, "delete", name, "Delete the screenshot"));
        return out;
    }

    private static IChatComponent action(String label, EnumChatFormatting color, String verb, String file, String hover) {
        ChatComponentText c = new ChatComponentText(label);
        c.setChatStyle(new ChatStyle().setColor(color).setBold(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/novoscreenshot " + verb + " " + file))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(hover))));
        return c;
    }

    /** Client-only command behind the chat buttons: /novoscreenshot open|copy|delete <file>. */
    public static final class Command extends CommandBase {
        @Override
        public String getCommandName() {
            return "novoscreenshot";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/novoscreenshot <open|copy|delete> <file>";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
            return true;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            File f = args.length >= 2 ? byName(String.join(" ", Arrays.copyOfRange(args, 1, args.length))) : null;
            if (f == null) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "That screenshot no longer exists."));
                return;
            }
            if ("open".equals(args[0])) {
                open(f);
            } else if ("copy".equals(args[0])) {
                sender.addChatMessage(new ChatComponentText(copy(f) ? EnumChatFormatting.GREEN + "Copied " + f.getName()
                        : EnumChatFormatting.RED + "Couldn't copy the image."));
            } else if ("delete".equals(args[0])) {
                sender.addChatMessage(new ChatComponentText(f.delete() ? EnumChatFormatting.GRAY + "Deleted " + f.getName()
                        : EnumChatFormatting.RED + "Couldn't delete " + f.getName()));
            }
        }
    }
}
