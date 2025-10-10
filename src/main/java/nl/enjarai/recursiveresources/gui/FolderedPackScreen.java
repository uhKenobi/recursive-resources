package nl.enjarai.recursiveresources.gui;

import com.google.common.collect.Lists;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.PackListWidget.ResourcePackEntry;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.text.Text;
import nl.enjarai.recursiveresources.RecursiveResources;
import nl.enjarai.recursiveresources.pack.FolderMeta;
import nl.enjarai.recursiveresources.pack.FolderPack;
import nl.enjarai.recursiveresources.util.ResourcePackListProcessor;
import nl.enjarai.recursiveresources.util.ResourcePackUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class FolderedPackScreen extends PackScreen {
    private static final Path ROOT_FOLDER = Path.of("");

    private static final Text OPEN_PACK_FOLDER = Text.translatable("pack.openFolder");
    private static final Text DONE = Text.translatable("gui.done");
    private static final Text SORT_AZ = Text.translatable("recursiveresources.sort.a-z");
    private static final Text SORT_ZA = Text.translatable("recursiveresources.sort.z-a");
    private static final Text VIEW_FOLDER = Text.translatable("recursiveresources.view.folder");
    private static final Text VIEW_FLAT = Text.translatable("recursiveresources.view.flat");
    private static final Text AVAILABLE_PACKS_TITLE_HOVER = Text.translatable("recursiveresources.availablepacks.title.hover");
    private static final Text SELECTED_PACKS_TITLE_HOVER = Text.translatable("recursiveresources.selectedpacks.title.hover");

    protected final MinecraftClient client = MinecraftClient.getInstance();
    protected final Screen parent;

    private final ResourcePackListProcessor listProcessor = new ResourcePackListProcessor(this::refresh);
    private Comparator<ResourcePackEntry> currentSorter;
    
    private FolderedPackListWidget customAvailablePacks;
    private FolderedPackListWidget customSelectedPacks;
    private ButtonWidget viewButton;

    private Path currentFolder = ROOT_FOLDER;
    private FolderMeta currentFolderMeta;
    private boolean folderView = true;
    public final List<Path> roots;

    public FolderedPackScreen(Screen parent, ResourcePackManager packManager, Consumer<ResourcePackManager> applier, File mainRoot, Text title, List<Path> roots) {
        super(packManager, applier, mainRoot.toPath(), title);
        this.parent = parent;
        this.roots = roots;
        this.currentFolderMeta = FolderMeta.loadMetaFile(roots, currentFolder);
        this.currentSorter = (pack1, pack2) -> Integer.compare(
                currentFolderMeta.sortEntry(pack1, currentFolder),
                currentFolderMeta.sortEntry(pack2, currentFolder)
        );
    }

    // Components
    @Override
    protected void init() {
        super.init();
        assert availablePackList != null;
        assert selectedPackList != null;
        assert searchBox != null;
        
        addDrawableChild(
            viewButton = ButtonWidget.builder(folderView ? VIEW_FOLDER : VIEW_FLAT, btn -> {
                folderView = !folderView;
                btn.setMessage(folderView ? VIEW_FOLDER : VIEW_FLAT);
                refresh();
                customAvailablePacks.setScrollY(0.0);
            })
            .dimensions(ButtonWidget.DEFAULT_WIDTH/2, ButtonWidget.DEFAULT_HEIGHT, ButtonWidget.DEFAULT_WIDTH/2 - 5, searchBox.getHeight())
            .build()
        );
        // Replacing the available pack list with our custom implementation
        PackListWidget originalAvailablePackList = availablePackList;
        remove(originalAvailablePackList);
        addDrawableChild(customAvailablePacks = new FolderedPackListWidget(client, this,
            availablePackList.getWidth(), availablePackList.getHeight(), availablePackList.title));
        // Make the title of the available packs selector clickable to load all packs
        customAvailablePacks.setCustomHeader(AVAILABLE_PACKS_TITLE_HOVER, null, () -> {
            for (PackListWidget.Entry entry : Lists.reverse(List.copyOf(customAvailablePacks.children()))) {
                if (entry instanceof ResourcePackEntry packEntry) {
                    if (packEntry.pack.canBeEnabled()) {
                        packEntry.pack.enable();
                    }
                }
            }
        });
        customAvailablePacks.setPosition(availablePackList.getX(), availablePackList.getY());
        availablePackList = customAvailablePacks;
        
        // Replacing the selected pack list with our custom implementation
        PackListWidget originalSelectedPackList = selectedPackList;
        remove(originalSelectedPackList);
        addDrawableChild(customSelectedPacks = new FolderedPackListWidget(client, this,
            selectedPackList.getWidth(), selectedPackList.getHeight(), selectedPackList.title));
        // Also make the selected packs title clickable to unload them
        customSelectedPacks.setCustomHeader(SELECTED_PACKS_TITLE_HOVER, null, () -> {
            for (PackListWidget.Entry entry : customSelectedPacks.children()) {
                if (entry instanceof ResourcePackEntry entry1) {
                    if ((this.currentFolderMeta.containsEntry(entry1, this.currentFolder) || currentFolder.equals(ROOT_FOLDER)) && entry1.pack.canBeDisabled()) {
                        entry1.pack.disable();
                    }
                }
            }
        });
        customSelectedPacks.setPosition(selectedPackList.getX(), selectedPackList.getY());
        selectedPackList = customSelectedPacks;

        listProcessor.pauseCallback();
        listProcessor.setSorter(currentSorter == null ? (currentSorter = ResourcePackListProcessor.sortAZ) : currentSorter);
        listProcessor.setFilter(searchBox.getText());
        listProcessor.resumeCallback();
        searchBox.setChangedListener(listProcessor::setFilter);
        
        this.refreshWidgetPositions();
    }
    
    @Override
    protected void refreshWidgetPositions() {
        super.refreshWidgetPositions();
        
        if (viewButton != null && searchBox != null){
            viewButton.setX(searchBox.getX() - viewButton.getWidth() - 3);
            viewButton.setY(searchBox.getY());
        }
    }

    @Override
    public void updatePackLists(@Nullable ResourcePackOrganizer.AbstractPack focused) {
        super.updatePackLists(focused);
        if (customAvailablePacks != null) {
            onFiltersUpdated();
        }
    }
    
    // Processing
    private Path getParentFileSafe(Path file) {
        var parent = file.getParent();
        return parent == null ? ROOT_FOLDER : parent;
    }

    private boolean notInRoot() {
        return folderView && !currentFolder.equals(ROOT_FOLDER);
    }

    private void onFiltersUpdated() {
        List<ResourcePackEntry> folders = new ArrayList<>();
        if (folderView) {
            // add a ".." entry when not in the root folder
            if (notInRoot()) {
                var rootFolder = getParentFileSafe(currentFolder);
                var meta = FolderMeta.loadMetaFile(roots, currentFolder);
                var entry = customAvailablePacks.createEntry(client,this, currentFolder, rootFolder, true, meta);
                folders.add(entry);
            }
            
            // create entries for all the folders that aren't packs
            var createdFolders = new ArrayList<Path>();
            for (Path root : roots) {
                var absolute = root.resolve(currentFolder);
                
                try (var contents = Files.list(absolute)) {
                    for (Path folder : contents.filter(ResourcePackUtils::isFolderButNotFolderBasedPack).toList()) {
                        var relative = root.relativize(folder.normalize());
                        
                        if (createdFolders.contains(relative)) {
                            continue;
                        }

                        var meta = FolderMeta.loadMetaFile(roots, relative);
                        
                        var entry = customAvailablePacks.createEntry(client,this, relative, null, false, meta);
                        if (((FolderPack) entry.pack).isVisible()) {
                            folders.add(entry);
                        }
    
                        createdFolders.add(relative);
                    }
                } catch (IOException e) {
                    RecursiveResources.LOGGER.error("Failed to read contents of " + absolute, e);
                }
            }
        }
        
        folders.forEach(packEntry -> customAvailablePacks.addEntry(packEntry));
        var toFilter = new ArrayList<ResourcePackEntry>();
        var noFilter = new ArrayList<PackListWidget.Entry>();
        for (PackListWidget.Entry entry : customAvailablePacks.children()) {
            if (entry instanceof ResourcePackEntry resourcePackEntry) {
                toFilter.add(resourcePackEntry);
            } else {
                noFilter.add(entry);
            }
        }
        customAvailablePacks.children.clear();
        customAvailablePacks.children.addAll(noFilter);
        List<ResourcePackEntry> fromFilter = new ArrayList<>();
        listProcessor.apply(toFilter, null, fromFilter);
        customAvailablePacks.children.addAll(fromFilter);

        // filter out all entries that aren't in the current folder
        if (folderView) {
            var filteredPacks = customAvailablePacks.children.stream().filter(entry -> {
                // if it's a folder, it's already relative, so we can check easily
                if (entry instanceof FolderedPackListWidget.FoldererResourcePackEntry folder) {
                    return folder.isUp || currentFolder.equals(getParentFileSafe(folder.folder));
                }
                
                // if it's a pack, we can use the FolderMeta to check if it should be shown
                if (entry instanceof ResourcePackEntry packEntry) {
                    return currentFolderMeta.containsEntry(packEntry, currentFolder);
                }
                return true;
            }).toList();

            customAvailablePacks.children.clear();
            customAvailablePacks.children.addAll(filteredPacks);
        }

        customAvailablePacks.setScrollY(customAvailablePacks.getScrollY());
    }

    public void moveToFolder(Path folder) {
        currentFolder = folder;
        currentFolderMeta = FolderMeta.loadMetaFile(roots, currentFolder);
        refresh();
        customAvailablePacks.setScrollY(0.0);
    }

    @Override
    public void close() {
        super.close();
        client.setScreen(parent);
        client.options.addResourcePackProfilesToManager(client.getResourcePackManager());
    }
}
