/*
 * Copyright (c) 2016 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 */

package gov.nasa.worldwindx;

import android.util.Log;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.layer.Layer;
import gov.nasa.worldwind.layer.LayerBuilder;
import gov.nasa.worldwind.layer.LayerFactory;
import gov.nasa.worldwind.util.Logger;

public class WmsLayerFragment extends BasicGlobeFragment {

    /**
     * Creates a new WorldWindow (GLSurfaceView) object with a WMS Layer
     *
     * @return The WorldWindow object containing the globe.
     */
    @Override
    public WorldWindow createWorldWindow() {
        // Let the super class (BasicGlobeFragment) do the creation
        WorldWindow wwd = super.createWorldWindow();

        // Create a layer Builder, a cool class that aspires to become
        // World Wind's general component for creating layers
        // from complex data sources.
        LayerBuilder layerBuilder = new LayerBuilder();

        // Create an OGC Web Map Service (WMS) layer to display the
        // surface temperature layer from NASA's Near Earth Observations WMS.

        layerBuilder.setLayerSource("wms")
                    .setPathOrAddres("http://neowms.sci.gsfc.nasa.gov/wms/wms")
                    .setLayerNames("\"MOD_LSTD_CLIM_M\"").setCallback(

                        new LayerBuilder.Callback() {
                            @Override
                            public void creationSucceeded(LayerBuilder builder, Layer layer) {
                                // Add the finished WMS layer to the World Window.
                                getWorldWindow().getLayers().addLayer(layer);
                                Log.i("gov.nasa.worldwind", "WMS layer creation succeeded");
                            }

                            @Override
                            public void creationFailed(LayerBuilder builder, Layer layer, Throwable ex) {
                                // Something went wrong connecting to the WMS server.
                                Log.e("gov.nasa.worldwind", "WMS layer creation failed", ex);
                            }
                        }
                    )
                    .build();

        return wwd;
    }
}
