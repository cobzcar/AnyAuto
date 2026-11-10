package com.mag.anyauto.car;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.constraints.ConstraintManager;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.GridItem;
import androidx.car.app.model.GridTemplate;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Template;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.mag.anyauto.Apps;
import com.mag.anyauto.Mirror;
import com.mag.anyauto.Prefs;
import com.mag.anyauto.Widgets;

import java.util.ArrayList;
import java.util.List;

/**
 * The apps chosen on the phone, as a grid by name; "Open" under the one on the display now. A
 * tap shows the app on the mirror screen below. Header: Back when an app is on the display
 * (back to it), the app icon on first use.
 */
final class AppsScreen extends Screen {
    // small icons: the whole template goes over binder in one parcel
    private static final int ICON_PX = 96;
    private final Mirror mirror;

    AppsScreen(CarContext c, Mirror mirror) {
        super(c);
        this.mirror = mirror;
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                // the grid is part of using AnyAuto: keep the phone dark while it shows
                mirror.hold(true);
                // apps ticked on the phone meanwhile
                invalidate();
            }

            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                mirror.hold(false);
            }
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        CarContext c = getCarContext();
        List<Apps.App> apps = Apps.chosen(c);
        // the YouTube shortcut: first in the grid, even when YouTube isn't ticked on the phone
        Apps.App youtube = Prefs.youtubeShortcut(c) ? Apps.youtube(c) : null;
        if (youtube != null) {
            List<Apps.App> pinned = new ArrayList<>();
            pinned.add(youtube);
            for (Apps.App a : apps) {
                if (!a.component.equals(youtube.component)) pinned.add(a);
            }
            apps = pinned;
        }
        ComponentName open = mirror.currentApp();
        GridItem widget = widgetTile(c, open);
        if (apps.isEmpty() && widget == null) {
            return new MessageTemplate.Builder(
                    "No apps chosen yet.\nOpen AnyAuto on the phone and tick the apps for the car.")
                    .setTitle("AnyAuto")
                    .setHeaderAction(Action.APP_ICON)
                    .setIcon(CarIcon.APP_ICON)
                    .build();
        }
        int limit = c.getCarService(ConstraintManager.class)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID);
        PackageManager pm = c.getPackageManager();
        ItemList.Builder list = new ItemList.Builder();
        // the widget chosen on the phone comes first and takes one place of the grid's limit
        if (widget != null) list.addItem(widget);
        for (int i = 0; i < Math.min(limit - (widget != null ? 1 : 0), apps.size()); i++) {
            Apps.App a = apps.get(i);
            CarIcon icon = new CarIcon.Builder(IconCompat.createWithBitmap(Apps.bitmap(a.icon(pm), ICON_PX))).build();
            GridItem.Builder item = new GridItem.Builder()
                    .setTitle(a.label)
                    .setImage(icon, GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener(() -> {
                        Prefs.setLast(c, a.component);
                        mirror.open(a.component);
                        getScreenManager().pop();
                    });
            if (a.component.equals(open)) item.setText("Open");
            list.addItem(item.build());
        }
        return new GridTemplate.Builder()
                .setTitle("Apps")
                .setHeaderAction(open != null ? Action.BACK : Action.APP_ICON)
                .setSingleList(list.build())
                .build();
    }

    /** The tile of the widget chosen on the phone (Poweramp's), or null when there is none. */
    private GridItem widgetTile(CarContext c, ComponentName open) {
        int id = Prefs.widgetId(c);
        if (id == -1) return null;
        try {
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(id);
            if (info == null) return null; // the widget is gone (Poweramp uninstalled, say)
            Drawable d = info.loadIcon(c, c.getResources().getDisplayMetrics().densityDpi);
            if (d == null) return null;
            ComponentName target = Widgets.component(c);
            CarIcon icon = new CarIcon.Builder(IconCompat.createWithBitmap(Apps.bitmap(d, ICON_PX))).build();
            GridItem.Builder item = new GridItem.Builder()
                    .setTitle(Prefs.widgetLabel(c))
                    .setImage(icon, GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener(() -> {
                        Prefs.setLast(c, target);
                        mirror.open(target);
                        getScreenManager().pop();
                    });
            if (target.equals(open)) item.setText("Open");
            return item.build();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
