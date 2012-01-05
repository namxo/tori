package org.vaadin.tori.widgetset.client.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.terminal.gwt.client.ApplicationConnection;
import com.vaadin.terminal.gwt.client.BrowserInfo;
import com.vaadin.terminal.gwt.client.Container;
import com.vaadin.terminal.gwt.client.Paintable;
import com.vaadin.terminal.gwt.client.RenderSpace;
import com.vaadin.terminal.gwt.client.StyleConstants;
import com.vaadin.terminal.gwt.client.UIDL;
import com.vaadin.terminal.gwt.client.VCaption;
import com.vaadin.terminal.gwt.client.ValueMap;
import com.vaadin.terminal.gwt.client.ui.VMarginInfo;

public class VLazyLayout extends SimplePanel implements Paintable, Container {
    public static final String TAGNAME = "lazylayout";
    public static final String CLASSNAME = "v-" + TAGNAME;

    public static final String VAR_LOAD_INDEXES_INTARR = "l";
    public static final String ATT_PAINT_INDICES_MAP = "p";
    public static final String ATT_PLACEHOLDER_HEIGHT_STRING = "h";
    public static final String ATT_PLACEHOLDER_WIDTH_STRING = "w";
    public static final String ATT_FETCH_INDEX_INT = "ff";
    public static final String ATT_TOTAL_COMPONENTS_INT = "c";
    public static final String ATT_PRIMARY_DISTANCE_INT = "pd";
    public static final String ATT_SECONDARY_DISTANCE_INT = "sd";

    private final FlowPane panel = new FlowPane();

    private final Element margin = DOM.createDiv();

    private boolean hasHeight;
    private boolean hasWidth;
    private boolean rendering;

    public VLazyLayout() {
        super();
        getElement().appendChild(margin);
        setStyleName(CLASSNAME);
        margin.setClassName(CLASSNAME + "-margin");
        setWidget(panel);
    }

    @Override
    protected Element getContainerElement() {
        return margin;
    }

    @Override
    public void setWidth(final String width) {
        super.setWidth(width);
        // panel.setWidth(width);
        hasWidth = width != null && !width.equals("");
        if (!rendering) {
            panel.updateRelativeSizes();
        }
    }

    @Override
    public void setHeight(final String height) {
        super.setHeight(height);
        // panel.setHeight(height);
        hasHeight = height != null && !height.equals("");
        if (!rendering) {
            panel.updateRelativeSizes();
        }
    }

    @Override
    public void updateFromUIDL(final UIDL uidl,
            final ApplicationConnection client) {
        rendering = true;

        if (client.updateComponent(this, uidl, true)) {
            rendering = false;
            return;
        }

        final VMarginInfo margins = new VMarginInfo(
                uidl.getIntAttribute("margins"));
        setStyleName(margin, CLASSNAME + "-" + StyleConstants.MARGIN_TOP,
                margins.hasTop());
        setStyleName(margin, CLASSNAME + "-" + StyleConstants.MARGIN_RIGHT,
                margins.hasRight());
        setStyleName(margin, CLASSNAME + "-" + StyleConstants.MARGIN_BOTTOM,
                margins.hasBottom());
        setStyleName(margin, CLASSNAME + "-" + StyleConstants.MARGIN_LEFT,
                margins.hasLeft());

        setStyleName(margin, CLASSNAME + "-" + "spacing",
                uidl.hasAttribute("spacing"));
        panel.updateFromUIDL(uidl, client);
        rendering = false;
    }

    @Override
    public boolean hasChildComponent(final Widget component) {
        return panel.hasChildComponent(component);
    }

    @Override
    public void replaceChildComponent(final Widget oldComponent,
            final Widget newComponent) {
        panel.replaceChildComponent(oldComponent, newComponent);
    }

    @Override
    public void updateCaption(final Paintable component, final UIDL uidl) {
        panel.updateCaption(component, uidl);
    }

    private static class PlaceholderWidget extends Label {

        public PlaceholderWidget(final String string) {
            super(string);
        }
    }

    private static class FlowPane extends FlowPanel {

        private static final int SECONDARY_FETCH_DELAY_MILLIS = 500;
        private static final int SCROLL_POLLING_PERIOD_MILLIS = 250;

        private final HashMap<Widget, VCaption> widgetToCaption = new HashMap<Widget, VCaption>();
        private ApplicationConnection client;
        private String id;
        private int lastIndex;

        private int primaryDistance;
        private int secondaryDistance;
        private String placeholderHeight;
        private String placeholderWidth;

        private int secondaryLoadGoingOn;

        private final Timer scrollPoller = new Timer() {
            @Override
            public void run() {
                findAllThingsToFetchAndFetchThem(primaryDistance);
                startSecondaryLoading();
            }
        };

        public FlowPane() {
            super();
            setStyleName(CLASSNAME + "-container");
        }

        public void updateRelativeSizes() {
            for (final Widget w : getChildren()) {
                if (w instanceof Paintable) {
                    client.handleComponentRelativeSize(w);
                }
            }
        }

        public void updateFromUIDL(final UIDL uidl,
                final ApplicationConnection client) {
            this.client = client;
            this.id = uidl.getId();

            if (uidl.hasAttribute(ATT_PAINT_INDICES_MAP)) {
                addLazyLoadedWidgets(uidl, client);
                checkAndUpdatePlaceholderSizes(uidl);
                startSecondaryLoading();
            } else {
                initialize(uidl);
                findAllThingsToFetchAndFetchThem(primaryDistance);
            }

            /*
             * We've gone through the client-server-client cycle. All rendering
             * is done. It's okay to start polling again
             */
            scrollPoller.scheduleRepeating(SCROLL_POLLING_PERIOD_MILLIS);
        }

        private void checkAndUpdatePlaceholderSizes(final UIDL uidl) {
            final String newPlaceholderHeight = uidl
                    .getStringAttribute(ATT_PLACEHOLDER_HEIGHT_STRING);
            final String newPlaceholderWidth = uidl
                    .getStringAttribute(ATT_PLACEHOLDER_WIDTH_STRING);

            if (!placeholderHeight.equals(newPlaceholderHeight)
                    || !placeholderWidth.equals(newPlaceholderWidth)) {
                placeholderHeight = newPlaceholderHeight;
                placeholderWidth = newPlaceholderWidth;

                for (final Widget placeholder : getChildren()) {
                    if (placeholder.getClass().equals(PlaceholderWidget.class)) {
                        placeholder.setWidth(placeholderWidth);
                        placeholder.setHeight(placeholderHeight);
                    }
                }
            }
        }

        private void initialize(final UIDL uidl) {
            final int componentAmount = uidl
                    .getIntAttribute(ATT_TOTAL_COMPONENTS_INT);
            placeholderHeight = uidl
                    .getStringAttribute(ATT_PLACEHOLDER_HEIGHT_STRING);
            placeholderWidth = uidl
                    .getStringAttribute(ATT_PLACEHOLDER_WIDTH_STRING);
            primaryDistance = uidl.getIntAttribute(ATT_PRIMARY_DISTANCE_INT);
            secondaryDistance = uidl
                    .getIntAttribute(ATT_SECONDARY_DISTANCE_INT);

            for (int i = getChildren().size(); i < componentAmount; i++) {
                add(createPlaceholderWidget());
            }
        }

        private PlaceholderWidget createPlaceholderWidget() {
            final PlaceholderWidget placeholder = new PlaceholderWidget(
                    "Loading...");
            placeholder.setWidth(placeholderWidth);
            placeholder.setHeight(placeholderHeight);
            placeholder.setStyleName(CLASSNAME + "-placeholder");
            placeholder.getElement().getStyle().setBackgroundColor("#ccc");
            return placeholder;
        }

        private void addLazyLoadedWidgets(final UIDL uidl,
                final ApplicationConnection client) {
            /*
             * this initializes the widgets so that they are actually created
             * from the UIDL
             */
            final Iterator<Object> children = uidl.getChildIterator();
            while (children.hasNext()) {
                final UIDL child = (UIDL) children.next();
                final Paintable paintable = client.getPaintable(child);
                paintable.updateFromUIDL(child, client);
            }

            final ValueMap componentPlaceMap = uidl
                    .getMapAttribute(ATT_PAINT_INDICES_MAP);
            for (final String key : componentPlaceMap.getKeySet()) {
                final Paintable paintable = client.getPaintable(key);
                final int placeIndex = componentPlaceMap.getInt(key);
                remove(placeIndex);
                insert((Widget) paintable, placeIndex);
            }
            updateRelativeSizes();
        }

        private void startSecondaryLoading() {
            if (secondaryLoadGoingOn == 0) {
                secondaryLoadGoingOn++;
                new Timer() {
                    @Override
                    public void run() {
                        secondaryLoadGoingOn--;
                        findAllThingsToFetchAndFetchThem(secondaryDistance);
                    }
                }.schedule(SECONDARY_FETCH_DELAY_MILLIS);
            }
        }

        private void findAllThingsToFetchAndFetchThem(final int distance) {
            final Set<Widget> componentsToLoad = new HashSet<Widget>();
            for (final Widget child : getChildren()) {
                final boolean isAPlaceholderWidget = child.getClass() == PlaceholderWidget.class;
                if (isAPlaceholderWidget && isBeingShown(child, distance)) {
                    componentsToLoad.add(child);
                }
            }

            if (!componentsToLoad.isEmpty()) {

                final Integer[] idsToLoad = new Integer[componentsToLoad.size()];
                int i = 0;
                for (final Widget widgetPlaceholder : componentsToLoad) {
                    idsToLoad[i] = getWidgetIndex(widgetPlaceholder);
                    i++;
                }

                client.updateVariable(id, VAR_LOAD_INDEXES_INTARR, idsToLoad,
                        false);
                sendToServer();
            }
        }

        private void sendToServer() {
            /*
             * We're going to the server. Let's stop the scroll poller, since
             * it's unnecessary to do that. And we don't want two requests going
             * out simultaneously. So stop, until we come back to the client
             * side.
             */
            scrollPoller.cancel();
            client.sendPendingVariableChanges();
        }

        private boolean isBeingShown(final Widget child, final int proximity) {

            final Element element = child.getElement();

            /*
             * track the original element's position as we iterate through the
             * DOM tree
             */
            int originalTopAdjusted = 0 - proximity;
            final int originalHeight = element.getOffsetHeight() + proximity;
            int originalLeftAdjusted = 0 - proximity;
            final int originalWidth = element.getOffsetWidth() + proximity;

            com.google.gwt.dom.client.Element childElement = element;
            com.google.gwt.dom.client.Element parentElement = element
                    .getParentElement();

            while (parentElement != null) {

                // clientheight == the height as seen in browser
                // offsetheight == the DOM element's native height

                // See if the parent sees the child
                final int parentTop = 0 + parentElement.getScrollTop();
                final int parentBottom = parentTop
                        + parentElement.getClientHeight();
                final int parentLeft = 0 + parentElement.getScrollLeft();
                final int parentRight = parentLeft
                        + parentElement.getClientWidth();

                /*
                 * renderbox is the box that indicates the boundary of what
                 * should be rendered - if parent is inside the renderbox, it's
                 * a candidate for rendering.
                 */
                final int renderBoxTop = childElement.getOffsetTop()
                        - proximity;
                final int renderBoxBottom = renderBoxTop
                        + childElement.getOffsetHeight() + proximity;
                final int renderBoxLeft = childElement.getOffsetLeft()
                        - proximity;
                final int renderBoxRight = renderBoxLeft
                        + childElement.getOffsetWidth() + proximity;

                if (!colliding2D(parentTop, parentRight, parentBottom,
                        parentLeft, renderBoxTop, renderBoxRight,
                        renderBoxBottom, renderBoxLeft)) {
                    return false;
                }

                /*
                 * see if the original component is visible from the parent.
                 * Move the object around to correspond the relative changes in
                 * position. The offset is always relative to the parent - not
                 * the canvas.
                 */
                originalTopAdjusted += childElement.getOffsetTop()
                        - childElement.getScrollTop();
                originalLeftAdjusted += childElement.getOffsetLeft()
                        - childElement.getScrollLeft();
                if (!colliding2D(parentTop, parentRight, parentBottom,
                        parentLeft, originalTopAdjusted, originalLeftAdjusted
                                + originalWidth, originalTopAdjusted
                                + originalHeight, originalLeftAdjusted)) {
                    return false;
                }

                childElement = parentElement;
                parentElement = childElement.getOffsetParent();
            }

            // lastly, check the browser itself.
            final int parentTop = Window.getScrollTop();
            final int parentBottom = parentTop + Window.getClientHeight();
            final int parentLeft = Window.getScrollLeft();
            final int parentRight = parentLeft + Window.getClientWidth();

            final int renderBoxTop = childElement.getOffsetTop() - proximity;
            final int renderBoxBottom = childElement.getOffsetTop()
                    + childElement.getClientHeight() + proximity;
            final int renderBoxLeft = childElement.getOffsetLeft() - proximity;
            final int renderBoxRight = childElement.getOffsetLeft()
                    + childElement.getClientWidth() + proximity;

            if (!colliding2D(parentTop, parentRight, parentBottom, parentLeft,
                    renderBoxTop, renderBoxRight, renderBoxBottom,
                    renderBoxLeft)) {
                return false;
            }

            originalTopAdjusted += childElement.getOffsetTop();
            originalLeftAdjusted += childElement.getOffsetLeft();
            if (!colliding2D(parentTop, parentRight, parentBottom, parentLeft,
                    originalTopAdjusted, originalLeftAdjusted + originalWidth,
                    originalTopAdjusted + originalHeight, originalLeftAdjusted)) {
                return false;
            }

            return true;
        }

        /**
         * Check whether a box overlaps (partially or completely) another.
         */
        private static boolean colliding2D(final int topA, final int rightA,
                final int bottomA, final int leftA, final int topB,
                final int rightB, final int bottomB, final int leftB) {

            final boolean verticalCollide = colliding1D(topA, bottomA, topB,
                    bottomB);
            final boolean horizontalCollide = colliding1D(leftA, rightA, leftB,
                    rightB);
            return verticalCollide && horizontalCollide;
        }

        /**
         * Check whether a line overlaps (partially or completely) another.
         */
        private static boolean colliding1D(final int startA, final int endA,
                final int startB, final int endB) {
            if (endA < startB) {
                return false;
            }
            if (startA > endB) {
                return false;
            }
            return true;
        }

        public boolean hasChildComponent(final Widget component) {
            return component.getParent() == this;
        }

        public void replaceChildComponent(final Widget oldComponent,
                final Widget newComponent) {
            final VCaption caption = widgetToCaption.get(oldComponent);
            if (caption != null) {
                remove(caption);
                widgetToCaption.remove(oldComponent);
            }
            final int index = getWidgetIndex(oldComponent);
            if (index >= 0) {
                remove(oldComponent);
                insert(newComponent, index);
            }
        }

        public void updateCaption(final Paintable component, final UIDL uidl) {
            VCaption caption = widgetToCaption.get(component);
            if (VCaption.isNeeded(uidl)) {
                final Widget widget = (Widget) component;
                if (caption == null) {
                    caption = new VCaption(component, client);
                    widgetToCaption.put(widget, caption);
                    insert(caption, getWidgetIndex(widget));
                    lastIndex++;
                } else if (!caption.isAttached()) {
                    insert(caption, getWidgetIndex(widget));
                    lastIndex++;
                }
                caption.updateCaption(uidl);
            } else if (caption != null) {
                remove(caption);
                widgetToCaption.remove(component);
            }
        }
    }

    private RenderSpace space;

    @Override
    public RenderSpace getAllocatedSpace(final Widget child) {
        if (space == null) {
            space = new RenderSpace(-1, -1) {
                @Override
                public int getWidth() {
                    if (BrowserInfo.get().isIE()) {
                        final int width = getOffsetWidth();
                        final int margins = margin.getOffsetWidth()
                                - panel.getOffsetWidth();
                        return width - margins;
                    } else {
                        return panel.getOffsetWidth();
                    }
                }

                @Override
                public int getHeight() {
                    final int height = getOffsetHeight();
                    final int margins = margin.getOffsetHeight()
                            - panel.getOffsetHeight();
                    return height - margins;
                }
            };
        }
        return space;
    }

    @Override
    public boolean requestLayout(final Set<Paintable> children) {
        if (hasSize()) {
            return true;
        } else {
            // Size may have changed
            // TODO optimize this: cache size if not fixed, handle both width
            // and height separately
            return false;
        }
    }

    private boolean hasSize() {
        return hasWidth && hasHeight;
    }

}
