package nl.enjarai.recursiveresources.gui;

import com.google.common.collect.Lists;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import nl.enjarai.recursiveresources.RecursiveResources;
import nl.enjarai.recursiveresources.pack.FolderMeta;
import nl.enjarai.recursiveresources.pack.FolderPack;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class FolderedPackListWidget extends PackListWidget {
    private Text textHover;
    @Nullable
    private Text textTooltip;
    @Nullable
    private Runnable titleClickEvent;
    
    public FolderedPackListWidget(MinecraftClient client, PackScreen screen, int width, int height, Text title) {
        super(client, screen, width, height, title);
    }
    
    public void setCustomHeader(Text textHover, Text textTooltip, Runnable titleClickEvent) {
        this.textHover = textHover;
        this.textTooltip = textTooltip;
        this.titleClickEvent = titleClickEvent;
    }
    
    @Override
    protected int addEntry(Entry entry) {
        return super.addEntry(entry);
    }
    
    @Override
    protected int addEntry(Entry entry, int entryHeight) {
        if (entry instanceof HeaderEntry) {
            Text text = Text.empty().append(this.title).formatted(Formatting.UNDERLINE, Formatting.BOLD);
            entry = new PackListWidget.HeaderEntry(this.client.textRenderer, text) {
                    @Override
                    public boolean mouseClicked(Click click, boolean doubled) {
                        if (super.mouseClicked(click, doubled)) {
                            if (titleClickEvent != null) {
                                titleClickEvent.run();
                            }
                            return true;
                        }
                        return false;
                    }
                    
                    @Override
                    public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
                        if (textHover != null) {
                            var text = Text.empty().append(textHover).formatted(Formatting.UNDERLINE, Formatting.BOLD, Formatting.ITALIC);
                            //int textWidth = client.textRenderer.getWidth(text);
                            
                            //int left = getX() + getWidth() / 2 - textWidth / 2;
                            int top = Math.min(this.getY() + 3, getY());
                            //int right = left + textWidth;
                            int bottom = top + client.textRenderer.fontHeight;
                            
                            if (mouseX >= getX() && mouseX <= getX() + getWidth() && mouseY >= top && mouseY <= bottom) {
                                context.drawCenteredTextWithShadow(client.textRenderer, text, this.getX() + this.getWidth() / 2, this.getContentMiddleY() - 9 / 2, Colors.WHITE);
                                if (textTooltip != null) {
                                    context.drawTooltip(List.of(textTooltip.asOrderedText()), mouseX, mouseY);
                                }
                                return;
                            }
                        }
                        super.render(context, mouseX, mouseY, hovered, deltaTicks);
                    }
                };
        }
        return super.addEntry(entry, entryHeight);
    }
    
    @Override
    protected void clearEntries() {
        super.clearEntries();
    }
    
    public FoldererResourcePackEntry createEntry(MinecraftClient client, FolderedPackScreen ownerScreen, Path folder, @Nullable Path rootFolder, boolean isUp, FolderMeta meta) {
        return new FoldererResourcePackEntry(client, ownerScreen.availablePackList, ownerScreen.selectedPackList, ownerScreen, folder, rootFolder, isUp, meta);
    }
    
    public class FoldererResourcePackEntry extends PackListWidget.ResourcePackEntry {
        public static final Identifier WIDGETS_TEXTURE = RecursiveResources.id("textures/gui/widgets.png");
        public static final String UP_TEXT = "..";
        
        private static final Text BACK_DESCRIPTION = Text.translatable("recursiveresources.folder.back");
        private static final Text FOLDER_DESCRIPTION = Text.translatable("recursiveresources.folder.folder");
        private static final Text ERRORED_NAME = Text.translatable("recursiveresources.folder.errored").formatted(Formatting.DARK_RED);
        private static final Text ERRORED_DESCRIPTION = Text.translatable("recursiveresources.folder.errored_description").formatted(Formatting.RED);
        
        private final FolderedPackScreen ownerScreen;
        public final Path folder;
        @Nullable
        public final Path rootFolder;
        public final boolean isUp;
        public final List<ResourcePackEntry> children;
        public final FolderMeta meta;
        private final PackListWidget selectedList;
        
        private static Function<Path, Path> getIconFileResolver(List<Path> roots, Path folder) {
            return iconPath -> {
                if (iconPath.isAbsolute()) {
                    return iconPath;
                } else {
                    for (var root : roots) {
                        var iconFile = root
                            .resolve(folder)
                            .resolve(iconPath);
                        
                        if (Files.exists(iconFile)) return iconFile;
                    }
                }
                return null;
            };
        }
        
        public FoldererResourcePackEntry(MinecraftClient client, PackListWidget availablePacks, PackListWidget selectedList, FolderedPackScreen ownerScreen, Path folder, @Nullable Path rootFolder, boolean isUp, FolderMeta meta) {
            super(
                client, availablePacks,
                new FolderPack(
                    meta.errored() ? ERRORED_NAME : Text.of(isUp ? UP_TEXT : String.valueOf(folder.getFileName())),
                    isUp ? BACK_DESCRIPTION : meta.errored() ? ERRORED_DESCRIPTION : FOLDER_DESCRIPTION,
                    getIconFileResolver(ownerScreen.roots, folder),
                    folder, meta
                )
            );
            this.selectedList = selectedList;
            this.ownerScreen = ownerScreen;
            this.folder = folder;
            this.rootFolder = rootFolder;
            this.isUp = isUp;
            this.meta = meta;
            this.children = resolveChildren();
        }
        
        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            double relativeMouseX = click.x() - (double) widget.getRowLeft();
            if (relativeMouseX <= 32.0D) {
                if (getChildren().isEmpty()) {
                    disableChildren();
                } else {
                    enableChildren();
                }
                return true;
            }
            
            if (!this.widget.scrollbarDragged) {
                ownerScreen.moveToFolder(this.isUp ? this.rootFolder : this.folder);
            }
            return true;
        }
        
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            if (pack instanceof FolderPack folderPack) {
                folderPack.setHovered(hovered);
            }
            super.render(context, mouseX, mouseY, hovered, deltaTicks);
            
            if (hovered) {
                context.fill(getX(), getY(), getX() + 32, getY() + 32, 0xa0909090);
                int relativeMouseX = mouseX - getX();
                context.drawTexture(RenderPipelines.GUI_TEXTURED, WIDGETS_TEXTURE, getX(), getY(), getChildren().isEmpty() ? 32.0F : 0.0F, relativeMouseX < 32 ? 32.0F : 0.0F, 32, 32, 256, 256);
            }
        }
        
        private void enableChildren() {
            for (PackListWidget.Entry entry : Lists.reverse(List.copyOf(getChildren()))) {
                if (entry instanceof ResourcePackEntry packEntry) {
                    if (packEntry.pack.canBeEnabled()) {
                        packEntry.pack.enable();
                    }
                }
            }
        }
        
        private void disableChildren() {
            for (PackListWidget.Entry entry : Lists.reverse(List.copyOf(this.selectedList.children()))) {
                if (entry instanceof ResourcePackEntry packEntry) {
                    if (this.meta.containsEntry(packEntry, this.folder) && packEntry.pack.canBeDisabled()) {
                        packEntry.pack.disable();
                    }
                }
            }
        }
        
        private List<ResourcePackEntry> getChildren() {
            return children;
        }
        
        private List<ResourcePackEntry> resolveChildren() {
            return widget.children().stream()
                .filter(entry -> !(entry instanceof FoldererResourcePackEntry)
                    && entry instanceof ResourcePackEntry packEntry && meta.containsEntry(packEntry, folder))
                .sorted(Comparator.comparingInt(entry -> meta.sortEntry((ResourcePackEntry) entry, folder)).reversed())
                .map(entry -> (ResourcePackEntry)entry)
                .toList();
        }
    }
}
