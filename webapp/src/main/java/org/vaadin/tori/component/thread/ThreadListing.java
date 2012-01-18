package org.vaadin.tori.component.thread;

import java.util.Iterator;
import java.util.List;

import org.vaadin.tori.category.CategoryPresenter;
import org.vaadin.tori.component.LazyLayout;
import org.vaadin.tori.data.entity.DiscussionThread;

import com.vaadin.ui.Component;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.Label;

/**
 * UI component for displaying a vertical hierarchical list of threads.
 */
@SuppressWarnings("serial")
public class ThreadListing extends CustomComponent {

    private static final String COLUMN_HEADER_TOPIC = "Topic";
    private static final String COLUMN_HEADER_STARTEDBY = "Started by";
    private static final String COLUMN_HEADER_POSTS = "Posts";
    private static final String COLUMN_HEADER_LATESTPOST = "Latest post";

    private static final String STYLE_FOLLOWING = "following";
    private static final String STYLE_COLUMN_HEADER = "column-header";
    private static final String STYLE_FIXED_COLUMNS = "fixed-column-headers";
    private static final String STYLE_COLUMN_HEADER_ROW = "column-header-row";

    private static final String PLACEHOLDER_WIDTH = "100%";
    private static final String PLACEHOLDER_HEIGHT = "40px";
    private static final int RENDER_DELAY_MILLIS = 1000;
    private static final int RENDER_DISTANCE_PX = 500;

    private final CategoryPresenter presenter;
    private final LazyLayout layout;

    public ThreadListing(final CategoryPresenter presenter) {
        this.presenter = presenter;
        setStyleName("thread-listing");

        layout = new LazyLayout();
        layout.setPlaceholderSize(PLACEHOLDER_HEIGHT, PLACEHOLDER_WIDTH);
        layout.setRenderDistance(RENDER_DISTANCE_PX);
        layout.setRenderDelay(RENDER_DELAY_MILLIS);
        layout.setStyleName("wrapper-layout");
        setCompositionRoot(layout);
    }

    public void setThreads(final List<DiscussionThread> threads) {
        layout.removeAllComponents();

        initColumnHeaders();

        int index = 0;
        for (final DiscussionThread thread : threads) {
            final ThreadListingRow row = new ThreadListingRow(thread, presenter);
            assignStyles(row);
            if (index < 10) {
                // add the 10 first rows eagerly
                layout.addComponentEagerly(row);
            } else {
                layout.addComponent(row);
            }

            index++;
        }
    }

    private void initColumnHeaders() {
        final CssLayout headerRow = new CssLayout();
        headerRow.setStyleName(STYLE_COLUMN_HEADER_ROW);

        headerRow.addComponent(createColumnHeader(COLUMN_HEADER_TOPIC));
        final CssLayout detailsHeaders = new CssLayout();
        detailsHeaders.setStyleName(STYLE_FIXED_COLUMNS);
        detailsHeaders
                .addComponent(createColumnHeader(COLUMN_HEADER_STARTEDBY));
        detailsHeaders.addComponent(createColumnHeader(COLUMN_HEADER_POSTS));
        detailsHeaders
                .addComponent(createColumnHeader(COLUMN_HEADER_LATESTPOST));
        headerRow.addComponent(detailsHeaders);
        layout.addComponentEagerly(headerRow);
    }

    private Label createColumnHeader(final String header) {
        final Label label = new Label(header);
        label.setSizeUndefined();
        label.setStyleName(header.toLowerCase().replaceAll(" ", "-"));
        label.addStyleName(STYLE_COLUMN_HEADER);
        return label;
    }

    private void assignStyles(final ThreadListingRow row) {
        if (presenter.userIsFollowing(row.getThread())) {
            row.addStyleName(STYLE_FOLLOWING);
        } else {
            row.removeStyleName(STYLE_FOLLOWING);
        }
    }

    private ThreadListingRow getRow(final DiscussionThread thread) {
        for (final Iterator<Component> it = layout.getComponentIterator(); it
                .hasNext();) {
            final Component row = it.next();
            if (row instanceof ThreadListingRow) {
                final ThreadListingRow rowInstance = (ThreadListingRow) row;
                if (rowInstance.getThread().equals(thread)) {
                    return rowInstance;
                }
            }
        }
        return null;
    }

    public void refresh(final DiscussionThread thread) {
        final ThreadListingRow row = getRow(thread);
        if (row != null) {
            final ThreadListingRow newRow = new ThreadListingRow(thread,
                    presenter);
            assignStyles(newRow);
            layout.replaceComponent(row, newRow);
        }
    }

    public void refreshStyles(final DiscussionThread thread) {
        final ThreadListingRow row = getRow(thread);
        if (row != null) {
            assignStyles(row);
        }
    }

}
