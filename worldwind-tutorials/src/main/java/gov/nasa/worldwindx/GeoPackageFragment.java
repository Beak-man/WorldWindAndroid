/*
 * Copyright (c) 2017 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 */

package gov.nasa.worldwindx;

import android.util.Log;

import java.io.File;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.layer.Layer;
import gov.nasa.worldwind.layer.LayerBuilder;
import gov.nasa.worldwind.layer.LayerFactory;

public class GeoPackageFragment extends BasicGlobeFragment {

    /**
     * Creates a new WorldWindow (GLSurfaceView) object with a GeoPackage Layer
     *
     * @return The WorldWindow object containing the globe.
     */
    @Override
    public WorldWindow createWorldWindow() {
        // Let the super class (BasicGlobeFragment) do the creation
        WorldWindow wwd = super.createWorldWindow();

        // Unpack the tutorial GeoPackage asset to the Android application cache. GeoPackage relies on the Android
        // SQLite library which operates only on files in the local Android filesystem.
        File geoPackageFile = TutorialUtil.unpackAsset(this.getContext(), "geopackage_tutorial.gpkg");

        // Create a layer Builder, a cool class that aspires to become
        // World Wind's general component for creating layers
        // from complex data sources.
        LayerBuilder layerBuilder = new LayerBuilder();

        // Create an OGC GeoPackage layer to display a high resolution monochromatic image of Naval Air Station Oceana
        // in Virginia Beach, VA.

        layerBuilder.setLayerSource("GeoPackage")
                    .setPathOrAddres(geoPackageFile.getPath())
                    .setCallback(
                        new LayerBuilder.Callback() {
                            @Override
                            public void creationSucceeded(LayerBuilder factory, Layer layer) {
                                // Add the finished GeoPackage layer to the World Window.
                                getWorldWindow().getLayers().addLayer(layer);
                                // Place the viewer directly over the GeoPackage image.
                                getWorldWindow().getNavigator().setLatitude(36.8139677556754);
                                getWorldWindow().getNavigator().setLongitude(-76.03260320181615);
                                getWorldWindow().getNavigator().setAltitude(20e3);
                                Log.i("gov.nasa.worldwind", "GeoPackage layer creation succeeded");
                            }

                            @Override
                            public void creationFailed(LayerBuilder factory, Layer layer, Throwable ex) {
                                // Something went wrong reading the GeoPackage.
                                Log.e("gov.nasa.worldwind", "GeoPackage layer creation failed", ex);
                            }
                        }
                    )
                    .build();
        return wwd;
    }
}
