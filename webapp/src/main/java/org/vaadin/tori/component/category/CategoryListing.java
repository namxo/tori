package org.vaadin.tori.component.category;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.vaadin.hene.popupbutton.PopupButton;
import org.vaadin.tori.ToriApplication;
import org.vaadin.tori.data.entity.Category;
import org.vaadin.tori.mvp.AbstractView;

import com.vaadin.terminal.ThemeResource;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.ComponentContainer;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

/**
 * UI component for displaying a vertical hierarchical list of categories.
 */
@SuppressWarnings("serial")
public class CategoryListing extends
        AbstractView<CategoryListingView, CategoryListingPresenter> implements
        CategoryListingView {

    public enum Mode {

        /**
         * Displays only a single column with all details inside it.
         */
        SINGLE_COLUMN,

        /**
         * Displays normally with a separate column for each detail.
         */
        NORMAL;

    }

    private final Mode mode;
    private CategoryTreeTable categoryTree;
    private CssLayout layout;
    private Component adminControls;
    private PopupButton createCategoryButton;
    private Button rearrangeCategoriesButton;
    private ComponentContainer rearrangeControls;

    public CategoryListing(final Mode listingMode) {
        this.mode = listingMode;

        // Must call init here as this View is not
        // instantiated by ToriNavigator.
        init(null, ToriApplication.getCurrent());
    }

    @Override
    protected Component createCompositionRoot() {
        layout = new CssLayout();
        return layout;
    }

    @Override
    public void initView() {
        categoryTree = new CategoryTreeTable(mode);
        layout.addComponent(categoryTree);

        adminControls = createAdminControls();
        layout.addComponent(adminControls);
    }

    @Override
    public void setAdminControlsVisible(final boolean visible) {
        adminControls.setVisible(visible);
    }

    public void setCategories(final List<Category> categories) {
        getPresenter().setCategories(categories);
    }

    @Override
    public void displayCategories(final List<Category> categories) {
        categoryTree.removeAllItems();
        for (final Category category : categories) {
            categoryTree.addCategory(category, null);
        }
    }

    private Component createAdminControls() {
        rearrangeCategoriesButton = new Button("Rearrange categories",
                new Button.ClickListener() {
                    @Override
                    public void buttonClick(final ClickEvent event) {
                        setRearranging(true);
                    }
                });
        rearrangeCategoriesButton.setIcon(new ThemeResource(
                "images/icon-rearrange.png"));
        createCategoryButton = new PopupButton("Create a new category");
        createCategoryButton.addListener(new Button.ClickListener() {
            @Override
            public void buttonClick(final ClickEvent event) {
                createCategory();
            }
        });
        createCategoryButton.setStyleName("popupIndicatorHidden");
        createCategoryButton.setIcon(new ThemeResource("images/icon-add.png"));
        createCategoryButton.setComponent(createNewCategoryForm());

        rearrangeControls = createRearrangeControls();
        rearrangeControls.setVisible(false);

        final HorizontalLayout buttonWrapper = new HorizontalLayout();
        buttonWrapper.setSpacing(true);
        buttonWrapper.addComponent(createCategoryButton);
        buttonWrapper.addComponent(rearrangeCategoriesButton);

        final HorizontalLayout adminControls = new HorizontalLayout();
        adminControls.setWidth("100%");
        adminControls.addComponent(buttonWrapper);
        adminControls.addComponent(rearrangeControls);
        adminControls.setComponentAlignment(rearrangeControls,
                Alignment.TOP_RIGHT);
        adminControls.setMargin(true, false, true, false);
        return adminControls;
    }

    private Component createNewCategoryForm() {
        final VerticalLayout newCategoryLayout = new VerticalLayout();
        newCategoryLayout.setSpacing(true);
        newCategoryLayout.setMargin(true);
        newCategoryLayout.setWidth("300px");

        final TextField nameField = new TextField();
        nameField.setInputPrompt("Category name");
        nameField.setWidth("100%");
        newCategoryLayout.addComponent(nameField);

        final TextArea descriptionField = new TextArea();
        descriptionField.setInputPrompt("Description");
        descriptionField.setRows(3);
        descriptionField.setWidth("100%");
        newCategoryLayout.addComponent(descriptionField);

        final Button saveButton = new Button("Create Category");
        newCategoryLayout.addComponent(saveButton);
        newCategoryLayout.setComponentAlignment(saveButton,
                Alignment.BOTTOM_RIGHT);

        return newCategoryLayout;
    }

    private ComponentContainer createRearrangeControls() {
        final HorizontalLayout rearrangeControls = new HorizontalLayout();
        rearrangeControls.setSpacing(true);
        rearrangeControls.addComponent(new Button("Apply rearrangement",
                new Button.ClickListener() {
                    @Override
                    public void buttonClick(final ClickEvent event) {
                        setRearranging(false);
                        getPresenter().applyRearrangement();
                    }
                }));
        rearrangeControls.addComponent(new Button("Cancel",
                new Button.ClickListener() {
                    @Override
                    public void buttonClick(final ClickEvent event) {
                        setRearranging(false);
                        getPresenter().cancelRearrangement();
                    }
                }));
        return rearrangeControls;
    }

    private void setRearranging(final boolean rearranging) {
        createCategoryButton.setEnabled(!rearranging);
        categoryTree.setDraggingEnabled(rearranging);
        rearrangeCategoriesButton.setVisible(!rearranging);
        rearrangeControls.setVisible(rearranging);
    }

    @Override
    public Set<Category> getModifiedCategories() {
        return getModifiedCategories(categoryTree.rootItemIds());
    }

    private Set<Category> getModifiedCategories(
            final Collection<?> itemIdsToCheck) {
        final Set<Category> changed = new HashSet<Category>();

        int index = 0;
        for (final Object itemId : itemIdsToCheck) {
            if (itemId instanceof Category) {
                // check the display order
                final Category category = (Category) itemId;
                if (category.getDisplayOrder() != index) {
                    // update the displayOrder property if reordered
                    category.setDisplayOrder(index);
                    changed.add(category);
                }

                // check the parent
                final Object parent = categoryTree.getParent(itemId);
                if (parent == null) {
                    final Category currentRoot = getPresenter()
                            .getCurrentRoot();
                    if ((currentRoot == null && category.getParentCategory() != null)
                            || (currentRoot != null && !currentRoot
                                    .equals(category.getParentCategory()))) {
                        category.setParentCategory(currentRoot);
                        changed.add(category);
                    }
                } else if (parent instanceof Category) {
                    final Category parentCategory = (Category) parent;
                    if (!parentCategory.equals(category.getParentCategory())) {
                        category.setParentCategory(parentCategory);
                        changed.add(category);
                    }
                }
                index++;
            }

            // recursively add changes from sub categories
            final Collection<?> subCategoryItemIds = categoryTree
                    .getChildren(itemId);
            if (subCategoryItemIds != null && !subCategoryItemIds.isEmpty()) {
                changed.addAll(getModifiedCategories(subCategoryItemIds));
            }
        }
        return changed;
    }

    private void createCategory() {
        // TODO
    }

    @Override
    protected CategoryListingPresenter createPresenter() {
        final CategoryListingPresenter presenter = new CategoryListingPresenter(
                ToriApplication.getCurrent().getDataSource());
        categoryTree.setPresenter(presenter);
        return presenter;
    }

    @Override
    protected void navigationTo(final String requestedDataId) {
        // NOP
    }

}