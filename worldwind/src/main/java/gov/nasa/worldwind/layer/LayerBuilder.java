/*
 * Copyright (c) 2017 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 */

package gov.nasa.worldwind.layer;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.ogc.WmsLayerConfig;
import gov.nasa.worldwind.ogc.WmsTileFactory;
import gov.nasa.worldwind.ogc.gpkg.GeoPackage;
import gov.nasa.worldwind.ogc.gpkg.GpkgContent;
import gov.nasa.worldwind.ogc.gpkg.GpkgSpatialReferenceSystem;
import gov.nasa.worldwind.ogc.gpkg.GpkgTileFactory;
import gov.nasa.worldwind.ogc.gpkg.GpkgTileMatrixSet;
import gov.nasa.worldwind.ogc.gpkg.GpkgTileUserMetrics;
import gov.nasa.worldwind.ogc.wms.WmsCapabilities;
import gov.nasa.worldwind.ogc.wms.WmsLayerCapabilities;
import gov.nasa.worldwind.shape.TiledSurfaceImage;
import gov.nasa.worldwind.util.LevelSet;
import gov.nasa.worldwind.util.LevelSetConfig;
import gov.nasa.worldwind.util.Logger;
import gov.nasa.worldwind.util.WWUtil;

public class LayerBuilder {
    public interface Callback {

        void creationSucceeded(LayerBuilder builder, Layer layer);

        void creationFailed(LayerBuilder builder, Layer layer, Throwable ex);
    }

    public LayerBuilder(){   
    }

    protected Handler mainLoopHandler = new Handler(Looper.getMainLooper());

    protected static final double DEFAULT_WMS_RADIANS_PER_PIXEL = 10.0 / WorldWind.WGS84_SEMI_MAJOR_AXIS;

    protected List<String> compatibleImageFormats = Arrays.asList("image/png", "image/jpg", "image/jpeg", "image/gif", "image/bmp");

    //TODO: LayerBuilder implementation goes here

    protected WmsCapabilities retrieveWmsCapabilities(String serviceAddress) throws Exception {
        InputStream inputStream = null;
        WmsCapabilities wmsCapabilities = null;
        try {
            // Build the appropriate request Uri given the provided service address
            Uri serviceUri = Uri.parse(serviceAddress).buildUpon()
                .appendQueryParameter("VERSION", "1.3.0")
                .appendQueryParameter("SERVICE", "WMS")
                .appendQueryParameter("REQUEST", "GetCapabilities")
                .build();

            // Open the connection as an input stream
            URLConnection conn = new URL(serviceUri.toString()).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(30000);
            inputStream = new BufferedInputStream(conn.getInputStream());

            // Parse and read the input stream
            wmsCapabilities = WmsCapabilities.getCapabilities(inputStream);
        } catch (Exception e) {
            throw new RuntimeException(
                Logger.makeMessage("LayerBuilder", "retrieveWmsCapabilities", "Unable to open connection and read from service address"));
        } finally {
            WWUtil.closeSilently(inputStream);
        }

        return wmsCapabilities;
    }

    protected WmsLayerConfig getLayerConfigFromWmsCapabilities(List<WmsLayerCapabilities> layerCapabilities) {
        // Construct the WmsTiledImage renderable from the WMS Capabilities properties
        WmsLayerConfig wmsLayerConfig = new WmsLayerConfig();
        WmsCapabilities wmsCapabilities = layerCapabilities.get(0).getServiceCapabilities();
        String version = wmsCapabilities.getVersion();
        if (version.equals("1.3.0")) {
            wmsLayerConfig.wmsVersion = version;
        } else if (version.equals("1.1.1")) {
            wmsLayerConfig.wmsVersion = version;
        } else {
            throw new RuntimeException(
                Logger.makeMessage("LayerBuilder", "getLayerConfigFromWmsCapabilities", "Version not compatible"));
        }

        String requestUrl = wmsCapabilities.getRequestURL("GetMap", "Get");
        if (requestUrl == null) {
            throw new RuntimeException(
                Logger.makeMessage("LayerBuilder", "getLayerConfigFromWmsCapabilities", "Unable to resolve GetMap URL"));
        } else {
            wmsLayerConfig.serviceAddress = requestUrl;
        }


        StringBuilder sb = null;
        Set<String> matchingCoordinateSystems = null;
        for (WmsLayerCapabilities layerCapability : layerCapabilities) {
            String layerName = layerCapability.getName();
            if (sb == null) {
                sb = new StringBuilder(layerName);
            } else {
                sb.append(",").append(layerName);
            }
            Set<String> layerCoordinateSystems = layerCapability.getReferenceSystem();
            if (matchingCoordinateSystems == null) {
                matchingCoordinateSystems = new HashSet<>();
                matchingCoordinateSystems.addAll(layerCoordinateSystems);
            } else {
                matchingCoordinateSystems.retainAll(layerCoordinateSystems);
            }
        }

        wmsLayerConfig.layerNames = sb.toString();

        if (matchingCoordinateSystems.contains("EPSG:4326")) {
            wmsLayerConfig.coordinateSystem = "EPSG:4326";
        } else if (matchingCoordinateSystems.contains("CRS:84")) {
            wmsLayerConfig.coordinateSystem = "CRS:84";
        } else {
            throw new RuntimeException(
                Logger.makeMessage("LayerBuilder", "getLayerConfigFromWmsCapabilities", "Coordinate systems not compatible"));
        }

        // Negotiate Image Formats
        Set<String> imageFormats = wmsCapabilities.getImageFormats();
        for (String compatibleImageFormat : this.compatibleImageFormats) {
            if (imageFormats.contains(compatibleImageFormat)) {
                wmsLayerConfig.imageFormat = compatibleImageFormat;
                break;
            }
        }

        if (wmsLayerConfig.imageFormat == null) {
            throw new RuntimeException(
                Logger.makeMessage("LayerBuilder", "getLayerConfigFromWmsCapabilities", "Image Formats Not Compatible"));
        }

        return wmsLayerConfig;
    }

    protected LevelSetConfig getLevelSetConfigFromWmsCapabilities(List<WmsLayerCapabilities> layerCapabilities) {
        LevelSetConfig levelSetConfig = new LevelSetConfig();

        double minScaleDenominator = Double.MAX_VALUE;
        double minScaleHint = Double.MAX_VALUE;
        Sector sector = new Sector();
        for (WmsLayerCapabilities layerCapability : layerCapabilities) {
            Double layerMinScaleDenominator = layerCapability.getMinScaleDenominator();
            if (layerMinScaleDenominator != null) {
                minScaleDenominator = Math.min(minScaleDenominator, layerMinScaleDenominator);
            }
            Double layerMinScaleHint = layerCapability.getMinScaleHint();
            if (layerMinScaleHint != null) {
                minScaleHint = Math.min(minScaleHint, layerMinScaleHint);
            }
            Sector layerSector = layerCapability.getGeographicBoundingBox();
            if (layerSector != null) {
                sector.union(layerSector);
            }
        }

        if (!sector.isEmpty()) {
            levelSetConfig.sector.set(sector);
        } else {
            throw new RuntimeException(
                Logger.makeMessage("LayerBuilder", "getLevelSetConfigFromWmsCapabilities", "Geographic Bounding Box Not Defined"));
        }

        if (minScaleDenominator != Double.MAX_VALUE) {
            // WMS 1.3.0 scale configuration. Based on the WMS 1.3.0 spec page 28. The hard coded value 0.00028 is
            // detailed in the spec as the common pixel size of 0.28mm x 0.28mm. Configures the maximum level not to
            // exceed the specified min scale denominator.
            double minMetersPerPixel = minScaleDenominator * 0.00028;
            double minRadiansPerPixel = minMetersPerPixel / WorldWind.WGS84_SEMI_MAJOR_AXIS;
            levelSetConfig.numLevels = levelSetConfig.numLevelsForMinResolution(minRadiansPerPixel);
        } else if (minScaleHint != Double.MAX_VALUE) {
            // WMS 1.1.1 scale configuration, where ScaleHint indicates approximate resolution in ground distance
            // meters. Configures the maximum level not to exceed the specified min scale denominator.
            double minMetersPerPixel = minScaleHint;
            double minRadiansPerPixel = minMetersPerPixel / WorldWind.WGS84_SEMI_MAJOR_AXIS;
            levelSetConfig.numLevels = levelSetConfig.numLevelsForMinResolution(minRadiansPerPixel);
        } else {
            // Default scale configuration when no minimum scale denominator or scale hint is provided.
            double defaultRadiansPerPixel = DEFAULT_WMS_RADIANS_PER_PIXEL;
            levelSetConfig.numLevels = levelSetConfig.numLevelsForResolution(defaultRadiansPerPixel);
        }

        return levelSetConfig;
    }

    //TODO: Refactor for LayerBuilder usage
    protected static class GeoPackageAsyncTask implements Runnable {

        protected LayerBuilder builder;

        protected String pathName;

        protected Layer layer;

        protected Callback callback;

        //TODO: Refactor to builder implementation
        public GeoPackageAsyncTask(LayerBuilder builder, String pathName, Layer layer, Callback callback) {
            this.builder = builder;
            this.pathName = pathName;
            this.layer = layer;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                //TODO: Refactor to builder implementation
                //this.builder.pathName().layer().callback();
                //this.builder.createFromGeoPackageAsync(this.pathName, this.layer, this.callback);
            } catch (final Throwable ex) {
                this.builder.mainLoopHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.creationFailed(builder, layer, ex);
                    }
                });
            }
        }
    }

    //TODO:Refactor for LayerBuilder usage
    protected static class WmsAsyncTask implements Runnable {

        protected LayerBuilder builder;

        protected String serviceAddress;

        protected List<String> layerNames;

        protected Layer layer;

        protected Callback callback;

        //TODO: Refactor to builder implementation
        public WmsAsyncTask(LayerBuilder builder, String serviceAddress, List<String> layerNames, Layer layer, Callback callback) {
            this.builder = builder;
            this.serviceAddress = serviceAddress;
            this.layerNames = layerNames;
            this.layer = layer;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                //TODO: Refactor to builder implementation
                //this.builder.serviceAddress().layerNames().layer().callback();
                //this.builder.createFromWmsAsync(this.serviceAddress, this.layerNames, this.layer, this.callback);
            } catch (final Throwable ex) {
                this.builder.mainLoopHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.creationFailed(builder, layer, ex);
                    }
                });
            }
        }
    }
}