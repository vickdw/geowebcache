/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 */

package org.geowebcache.layer.wms;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.parameters.ParameterFilter;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.ExpirationRule;
import org.geowebcache.layer.GridLocObj;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.ServletUtils;

/**
 * A tile layer backed by a WMS server
 */
public class WMSLayer extends TileLayer {

    public enum RequestType {
        MAP, FEATUREINFO
    };

    private String[] wmsUrl = null;

    private Integer concurrency = null;

    private String wmsLayers = null;

    protected Integer gutter;

    private String errorMime;

    private String wmsVersion;

    private String httpUsername;

    private String httpPassword;

    private String proxyUrl;

    // Not used, should be removed through XSL
    @SuppressWarnings("unused")
    private Boolean tiled;

    private Boolean transparent;

    private String bgColor;

    private String palette;

    private String vendorParameters;

    // Not used, should be removed through XSL
    @SuppressWarnings("unused")
    private String cachePrefix;

    protected String sphericalMercatorOverride;

    // private transient int expireCacheInt = -1;

    // private transient int expireClientsInt = -1;

    private transient int curWmsURL;

    private transient Lock layerLock;

    private transient boolean layerLocked;

    private transient Condition layerLockedCond;

    private transient Condition[] gridLocConds;

    private transient HashMap<GridLocObj, Boolean> procQueue;

    private transient ParameterFilter[] sortedModParams;

    private transient String[] sortedModParamsKeys;

    private transient boolean stylesIsModParam = false;

    private transient String encodedWMSLayers;

    private transient String encodedWMSStyles;

    private transient String encodedPalette;

    private transient String encodedName;

    private transient WMSSourceHelper sourceHelper = null;

    private static transient Log log = LogFactory.getLog(org.geowebcache.layer.wms.WMSLayer.class);

    /**
     * Note XStream uses reflection, this is only used for testing and loading from getCapabilities
     * 
     * @param layerName
     * @param cacheFactory
     * @param wmsURL
     * @param wmsStyles
     * @param wmsLayers
     * @param mimeFormats
     * @param grids
     * @param metaWidthHeight
     * @param vendorParams
     */
    public WMSLayer(String layerName, String[] wmsURL, String wmsStyles, String wmsLayers,
            List<String> mimeFormats, Hashtable<String, GridSubset> subSets,
            List<ParameterFilter> parameterFilters, int[] metaWidthHeight, String vendorParams,
            boolean queryable) {

        name = layerName;
        this.wmsUrl = wmsURL;
        this.wmsLayers = wmsLayers;
        this.wmsStyles = wmsStyles;
        this.mimeFormats = mimeFormats;
        this.subSets = subSets;
        this.parameterFilters = parameterFilters;
        this.metaWidthHeight = metaWidthHeight;
        this.vendorParameters = vendorParams;
        this.transparent = true;
        // this.bgColor = "0x000000";
        // this.palette = "test.png";
        this.queryable = queryable;
    }

    /**
     * @see org.geowebcache.layer.TileLayer#initializeInternal(org.geowebcache.grid.GridSetBroker)
     */
    @Override
    protected boolean initializeInternal(GridSetBroker gridSetBroker) {

        if (null == this.sourceHelper) {
            log.warn(this.name
                    + " is configured without a source, which is a bug unless you're running tests that don't care.");
        }

        curWmsURL = 0;

        if (backendTimeout == null) {
            backendTimeout = 120;
        }

        layerLock = new ReentrantLock();
        layerLockedCond = layerLock.newCondition();
        procQueue = new HashMap<GridLocObj, Boolean>();

        if (this.metaWidthHeight == null || this.metaWidthHeight.length != 2) {
            this.metaWidthHeight = new int[2];
            this.metaWidthHeight[0] = 3;
            this.metaWidthHeight[1] = 3;
        }

        // Create conditions for tile locking
        if (concurrency == null) {
            concurrency = 32;
        }

        // TODO There should be a WMSServer object and it should be on that
        this.gridLocConds = new Condition[concurrency];

        for (int i = 0; i < gridLocConds.length; i++) {
            gridLocConds[i] = layerLock.newCondition();
        }

        if (this.parameterFilters != null && this.parameterFilters.size() > 0) {
            Iterator<ParameterFilter> iter = parameterFilters.iterator();
            TreeMap<String, ParameterFilter> tree = new TreeMap<String, ParameterFilter>();

            while (iter.hasNext()) {
                ParameterFilter modParam = iter.next();
                String key = modParam.getKey();

                // STYLES is special because it is mandatory, so we need to make
                // a special case
                if (key.equalsIgnoreCase("STYLES")) {
                    stylesIsModParam = true;
                }
                tree.put(modParam.getKey(), modParam);
            }

            // this.sortedModParams = new
            // ModifiableParameter[this.modifiableParameters.size()];
            int arSize = tree.values().size();
            Iterator<ParameterFilter> sortedIter = tree.values().iterator();
            this.sortedModParams = new ParameterFilter[arSize];
            for (int i = 0; i < arSize; i++) {
                sortedModParams[i] = sortedIter.next();
            }

            this.sortedModParamsKeys = new String[sortedModParams.length];

            for (int i = 0; i < sortedModParams.length; i++) {
                sortedModParamsKeys[i] = sortedModParams[i].getKey();
            }

        }

        if (this.sourceHelper instanceof WMSHttpHelper) {
            for (int i = 0; i < wmsUrl.length; i++) {
                String url = wmsUrl[i];
                if (!url.contains("?")) {
                    wmsUrl[i] = url + "?";
                }
            }
        }

        if (gutter == null) {
            gutter = Integer.valueOf(0);
        }

        if (this.requestFilters != null) {
            Iterator<RequestFilter> iter = requestFilters.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().initialize(this);
                } catch (GeoWebCacheException e) {
                    log.error(e.getMessage());
                }
            }
        }

        if (wmsLayers != null && wmsLayers.length() > 0) {
            encodedWMSLayers = ServletUtils.URLEncode(wmsLayers);
        } else {
            encodedName = ServletUtils.URLEncode(name);
        }

        if (wmsStyles != null) {
            encodedWMSStyles = ServletUtils.URLEncode(wmsStyles);
        }

        if (palette != null) {
            encodedPalette = ServletUtils.URLEncode(palette);
        }

        return true;
    }

    public Resource getFeatureInfo(ConveyorTile convTile, BoundingBox bbox, int height, int width,
            int x, int y) throws GeoWebCacheException {
        return sourceHelper.makeFeatureInfoRequest(convTile, bbox, height, width, x, y);
    }

    /**
     * The main function
     * 
     * 1) Create cache key, test whether we can retrieve without locking 2) Get lock for metatile,
     * monitor condition variable if not (Recheck cache after signal) 3) Create metatile request,
     * execute 4) Get tiles and save them to cache 5) Unlock metatile, signal other threads 6) Set
     * Cache-Control, return tile
     * 
     * @param wmsparams
     * @return
     * @throws OutsideCoverageException
     */
    public ConveyorTile getTile(ConveyorTile tile) throws GeoWebCacheException, IOException,
            OutsideCoverageException {
        MimeType mime = tile.getMimeType();

        if (mime == null) {
            mime = this.formats.get(0);
        }

        if (!formats.contains(mime)) {
            throw new GeoWebCacheException(mime.getFormat() + " is not a supported format for "
                    + name);
        }

        String tileGridSetId = tile.getGridSetId();

        long[] gridLoc = tile.getTileIndex();

        // Final preflight check, throws exception if necessary
        getGridSubset(tileGridSetId).checkCoverage(gridLoc);

        ConveyorTile returnTile;

        if (tryCacheFetch(tile)) {
            returnTile = finalizeTile(tile);
        } else if (mime.supportsTiling()) { // Okay, so we need to go to the backend
            returnTile = getMetatilingReponse(tile, true);
        } else {
            returnTile = getNonMetatilingReponse(tile, true);
        }

        sendTileRequestedEvent(returnTile);

        return returnTile;
    }

    /**
     * Used for seeding
     */
    public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException,
            IOException {
        if (tile.getMimeType().supportsTiling()
                && (metaWidthHeight[0] > 1 || metaWidthHeight[1] > 1)) {
            getMetatilingReponse(tile, tryCache);
        } else {
            getNonMetatilingReponse(tile, tryCache);
        }
    }

    /**
     * Metatiling request forwarding
     * 
     * @param tile
     *            the Tile with all the information
     * @param tryCache
     *            whether to try the cache, or seed
     * @throws GeoWebCacheException
     */
    private ConveyorTile getMetatilingReponse(ConveyorTile tile, boolean tryCache)
            throws GeoWebCacheException {

        // int idx = this.getSRSIndex(tile.getSRS());
        long[] gridLoc = tile.getTileIndex();

        GridSubset gridSubset = subSets.get(tile.getGridSetId());

        // GridCalculator gridCalc = getGrid(tile.getSRS()).getGridCalculator();

        MimeType mimeType = tile.getMimeType();
        WMSMetaTile metaTile = new WMSMetaTile(this, gridSubset, mimeType,
                this.getFormatModifier(tile.getMimeType()), gridLoc, metaWidthHeight[0],
                metaWidthHeight[1], tile.getFullParameters());

        // Leave a hint to save expiration, if necessary
        if (saveExpirationHeaders) {
            metaTile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
        }

        long[] metaGridLoc = metaTile.getMetaGridPos();
        GridLocObj metaGlo = new GridLocObj(metaGridLoc, this.gridLocConds.length);

        /** ****************** Acquire lock ******************* */
        waitForQueue(metaGlo);
        /** ****************** Check cache again ************** */
        if (tryCache && tryCacheFetch(tile)) {
            // Someone got it already, return lock and we're done
            removeFromQueue(metaGlo);
            return finalizeTile(tile);
        }

        /*
         * This thread's byte buffer
         */
        ByteArrayResource buffer = getImageBuffer(WMS_BUFFER);

        try {
            /** ****************** No luck, Request metatile ****** */
            // Leave a hint to save expiration, if necessary
            if (saveExpirationHeaders) {
                metaTile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
            }

            sourceHelper.makeRequest(metaTile, buffer);

            if (metaTile.getError()) {
                throw new GeoWebCacheException("Empty metatile, error message: "
                        + metaTile.getErrorMessage());
            }

            if (saveExpirationHeaders) {
                // Converting to seconds
                saveExpirationInformation((int) (tile.getExpiresHeader() / 1000));
            }

            metaTile.setImageBytes(buffer);

            saveTiles(metaTile, tile);

            /** ****************** Return lock and response ****** */
        } finally {
            removeFromQueue(metaGlo);
        }
        return finalizeTile(tile);
    }

    /**
     * Non-metatiling forward to backend
     * 
     * @param tile
     *            the Tile with all the information
     * @param tryCache
     *            whether to try the cache, or seed
     * @throws GeoWebCacheException
     */
    private ConveyorTile getNonMetatilingReponse(ConveyorTile tile, boolean tryCache)
            throws GeoWebCacheException {
        // String debugHeadersStr = null;
        long[] gridLoc = tile.getTileIndex();
        GridLocObj glo = new GridLocObj(gridLoc, this.gridLocConds.length);

        /** ****************** Acquire lock ******************* */
        waitForQueue(glo);
        try {
            /** ****************** Check cache again ************** */
            if (tryCache && tryCacheFetch(tile)) {
                // Someone got it already, return lock and we're done
                removeFromQueue(glo);
                return tile;
                // return this.createTileResponse(tile.getData(), -1, mime,
                // response);
            }

            /** ****************** Tile ******************* */
            // String requestURL = null;
            // Leave a hint to save expiration, if necessary
            if (saveExpirationHeaders) {
                tile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
            }

            tile = doNonMetatilingRequest(tile);

            if (tile.getStatus() > 299
                    || this.getExpireCache((int) gridLoc[2]) != GWCVars.CACHE_DISABLE_CACHE) {
                tile.persist();
            }

            if (saveExpirationHeaders) {
                // Converting to seconds in the process
                saveExpirationInformation((int) (tile.getExpiresHeader() / 1000));
            }

            /** ****************** Return lock and response ****** */
        } finally {
            removeFromQueue(glo);
        }
        return finalizeTile(tile);
    }

    public boolean tryCacheFetch(ConveyorTile tile) {
        int expireCache = this.getExpireCache((int) tile.getTileIndex()[2]);
        if (expireCache != GWCVars.CACHE_DISABLE_CACHE) {
            try {
                return tile.retrieve(expireCache * 1000L);
            } catch (GeoWebCacheException gwce) {
                log.error(gwce.getMessage());
                tile.setErrorMsg(gwce.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * Uses the HTTP 1.1 spec to set expiration headers
     * 
     * @param response
     */
    public void setExpirationHeader(HttpServletResponse response, int zoomLevel) {
        int expireValue = this.getExpireClients(zoomLevel);

        // Fixed value
        if (expireValue == GWCVars.CACHE_VALUE_UNSET) {
            return;
        }

        // TODO move to TileResponse
        if (expireValue > 0) {
            response.setHeader("Cache-Control", "max-age=" + expireValue + ", must-revalidate");
            response.setHeader("Expires", ServletUtils.makeExpiresHeader(expireValue));
        } else if (expireValue == GWCVars.CACHE_NEVER_EXPIRE) {
            long oneYear = 3600 * 24 * 365;
            response.setHeader("Cache-Control", "max-age=" + oneYear);
            response.setHeader("Expires", ServletUtils.makeExpiresHeader((int) oneYear));
        } else if (expireValue == GWCVars.CACHE_DISABLE_CACHE) {
            response.setHeader("Cache-Control", "no-cache");
        } else if (expireValue == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
            int seconds = 3600;
            response.setHeader("geowebcache-error", "No real CacheControl information available");
            response.setHeader("Cache-Control", "max-age=" + seconds);
            response.setHeader("Expires", ServletUtils.makeExpiresHeader(seconds));
        }
    }

    public void setTileIndexHeader(ConveyorTile tile) {
        tile.servletResp.addHeader("geowebcache-tile-index", Arrays.toString(tile.getTileIndex()));
    }

    public ConveyorTile doNonMetatilingRequest(ConveyorTile tile) throws GeoWebCacheException {
        tile.setTileLayer(this);

        ByteArrayResource buffer = getImageBuffer(WMS_BUFFER);
        sourceHelper.makeRequest(tile, buffer);

        if (tile.getError() || buffer.getSize() == 0) {
            throw new GeoWebCacheException("Empty tile, error message: " + tile.getErrorMessage());
        }

        tile.setBlob(buffer);
        return tile;
    }

    private ConveyorTile finalizeTile(ConveyorTile tile) {
        if (tile.getStatus() == 0 && !tile.getError()) {
            tile.setStatus(200);
        }

        if (tile.servletResp != null) {
            setExpirationHeader(tile.servletResp, (int) tile.getTileIndex()[2]);
            setTileIndexHeader(tile);
        }

        return tile;
    }

    protected void saveExpirationInformation(int backendExpire) {
        this.saveExpirationHeaders = false;

        try {
            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if (backendExpire == -1) {
                    this.expireCacheList.set(0, new ExpirationRule(0, 7200));
                    log.error("Layer profile wants MaxAge from backend,"
                            + " but backend does not provide this. Setting to 7200 seconds.");
                } else {
                    this.expireCacheList.set(backendExpire, new ExpirationRule(0, 7200));
                }
                log.trace("Setting expireCache to: " + expireCache);
            }
            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if (backendExpire == -1) {
                    this.expireClientsList.set(0, new ExpirationRule(0, 7200));
                    log.error("Layer profile wants MaxAge from backend,"
                            + " but backend does not provide this. Setting to 7200 seconds.");
                } else {
                    this.expireClientsList.set(0, new ExpirationRule(0, backendExpire));
                    log.trace("Setting expireClients to: " + expireClients);
                }

            }
        } catch (Exception e) {
            // Sometimes this doesn't work (network conditions?),
            // and it's really not worth getting caught up on it.
            e.printStackTrace();
        }
    }

    /**
     * Returns the default image format if strFormat is unset
     * 
     * @param strFormat
     * @return ImageFormat equivalent, or default ImageFormat
     */
    public MimeType getImageFormat(String strFormat) throws MimeException {
        if (strFormat == null) {
            return this.formats.get(0);
        } else {
            return MimeType.createFromFormat(strFormat);
        }
    }

    public String getWMSRequestTemplate(MimeType responseFormat, RequestType reqType) {
        FormatModifier mod = getFormatModifier(responseFormat);

        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append("SERVICE=WMS");

        strBuilder.append("&REQUEST=");
        if (reqType == RequestType.MAP) {
            strBuilder.append("GetMap");
        } else { // if(reqType == RequestType.FEATUREINFO) {
            strBuilder.append("GetFeatureInfo");
        }

        strBuilder.append("&VERSION=");
        if (wmsVersion != null) {
            strBuilder.append(wmsVersion);
        } else {
            strBuilder.append("1.1.0");
        }

        strBuilder.append("&LAYERS=");

        if (this.wmsLayers != null && this.wmsLayers.length() != 0) {
            strBuilder.append(encodedWMSLayers);
        } else {
            strBuilder.append(encodedName);
        }

        if (reqType == RequestType.FEATUREINFO) {
            strBuilder.append("&QUERY_LAYERS=");

            if (this.wmsLayers != null && this.wmsLayers.length() != 0) {
                strBuilder.append(encodedWMSLayers);
            } else {
                strBuilder.append(encodedName);
            }
        }

        strBuilder.append("&EXCEPTIONS=");
        if (errorMime != null) {
            strBuilder.append(errorMime);
        } else {
            strBuilder.append(XMLMime.ogcxml.getMimeType());
        }

        if (!stylesIsModParam) {
            strBuilder.append("&STYLES=");
            if (wmsStyles != null && wmsStyles.length() != 0) {
                strBuilder.append(encodedWMSStyles);
            }
        }

        if (reqType == RequestType.MAP) {
            Boolean tmpTransparent = transparent;

            if (mod != null && mod.getTransparent() != null) {
                tmpTransparent = mod.getTransparent();
            }

            if (tmpTransparent == null || tmpTransparent) {
                strBuilder.append("&TRANSPARENT=").append("TRUE");
            } else {
                strBuilder.append("&TRANSPARENT=").append("FALSE");
            }

            String tmpBgColor = bgColor;
            if (mod != null && mod.getBgColor() != null) {
                tmpBgColor = mod.getBgColor();
            }

            if (tmpBgColor != null && tmpBgColor.length() != 0) {
                strBuilder.append("&BGCOLOR=").append(tmpBgColor);
            }

            String tmpPalette = encodedPalette;
            if (mod != null && mod.getPalette() != null) {
                tmpPalette = mod.getPalette();
            }

            if (tmpPalette != null && tmpPalette.length() != 0) {
                strBuilder.append("&PALETTE=").append(tmpPalette);
            }
        }

        if (vendorParameters != null && vendorParameters.length() != 0) {
            if (!vendorParameters.startsWith("&"))
                strBuilder.append("&");

            strBuilder.append(vendorParameters);
        }

        return strBuilder.toString();
    }

    /**
     * Get the WMS backend URL that should be used next according to the round robin.
     * 
     * @return the next URL
     */
    protected String nextWmsURL() {
        curWmsURL = (curWmsURL + 1) % wmsUrl.length;
        return wmsUrl[curWmsURL];
    }

    public long[][] getZoomedInGridLoc(String gridSetId, long[] gridLoc)
            throws GeoWebCacheException {
        return null;
    }

    /**
     * Acquires lock for the entire layer, returns only after all other requests that could write to
     * the queue have finished
     */
    public void acquireLayerLock() {
        if (layerLock == null) {
            layerLocked = true;
            return;
        }

        boolean wait = true;
        // Wait until the queue is free
        while (wait) {
            try {
                layerLock.lock();
                this.layerLocked = true;
                if (this.procQueue == null || this.procQueue.size() == 0) {
                    wait = false;
                }
            } finally {
                layerLock.unlock();
            }
        }
    }

    /**
     * Releases lock for the entire layer, signals threads that have been kept waiting
     */
    public void releaseLayerLock() {
        if (layerLock == null) {
            layerLocked = false;
            return;
        }

        layerLock.lock();
        try {
            layerLocked = false;
            // Wake everyone up
            layerLockedCond.signalAll();
        } finally {
            layerLock.unlock();
        }
    }

    /**
     * 
     * @param metaGridLoc
     * @return
     */
    protected boolean waitForQueue(GridLocObj glo) {
        boolean retry = true;
        boolean hasWaited = false;
        // int condIdx = getLocCondIdx(gridLoc);

        while (retry) {
            layerLock.lock();
            try {
                // Check for global lock
                if (layerLocked) {
                    this.layerLockedCond.await();
                } else if (this.procQueue.containsKey(glo)) {
                    // System.out.println(Thread.currentThread().getId()
                    // + " WAITING FOR "+glo.toString()+ " convar " + condIdx);
                    hasWaited = true;
                    this.gridLocConds[glo.hashCode()].await();
                    // System.out.println(Thread.currentThread().getId()
                    // + " WAKING UP "+glo.toString()+ " convar " + condIdx);
                } else {
                    this.procQueue.put(glo, true);
                    retry = false;
                    // System.out.println(Thread.currentThread().getId()
                    // + " CONTINUES "+glo.toString()+ " convar " + condIdx);
                }
            } catch (InterruptedException ie) {
                // Do we care? Maybe if the program is about to shut down
            } finally {
                layerLock.unlock();
            }
        }

        return hasWaited;
    }

    /**
     * Synchronization function, ensures that the same metatile is not requested simultaneously by
     * two threads.
     * 
     * @param gridLoc
     *            the grid positions of the tile (bottom left of metatile)
     * @return
     */
    protected void removeFromQueue(GridLocObj glo) {
        layerLock.lock();
        try {
            // System.out.println(Thread.currentThread().getId()
            // + " DONE, SIGNALLING "+glo.toString() + " convar " + condIdx);
            this.procQueue.remove(glo);
            this.gridLocConds[glo.hashCode()].signalAll();
        } finally {
            layerLock.unlock();
        }
    }

    public void setErrorMime(String errormime) {
        this.errorMime = errormime;
    }

    public void addMetaWidthHeight(int w, int h) {
        this.metaWidthHeight[0] = w;
        this.metaWidthHeight[1] = h;
    }

    public void setWMSurl(String[] wmsurl) {
        this.wmsUrl = wmsurl;
    }

    public String[] getWMSurl() {
        return this.wmsUrl;
    }

    public String getHttpPassword() {
        return httpPassword;
    }

    public String getHttpUsername() {
        return httpUsername;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    /**
     * Mandatory
     */
    public void setSourceHelper(WMSSourceHelper source) {
        log.debug("Setting sourceHelper on " + this.name);
        this.sourceHelper = source;
    }

    public WMSSourceHelper getSourceHelper() {
        return sourceHelper;
    }

    public void setVersion(String version) {
        this.wmsVersion = version;
    }

    public void setTiled(boolean tiled) {
        this.tiled = tiled;
    }

    public boolean getTransparent() {
        if (transparent == null || transparent) {
            return true;
        } else {
            return false;
        }
    }

    public void setTransparent(boolean transparent) {
        this.transparent = transparent;
    }

    public int[] getBackgroundColor() {
        if (bgColor == null || transparent != null && transparent) {
            return null;
        }
        int[] ret = new int[3];
        // 0xRRGGBB
        ret[0] = Integer.parseInt(bgColor.substring(2, 4), 16);
        ret[1] = Integer.parseInt(bgColor.substring(4, 6), 16);
        ret[2] = Integer.parseInt(bgColor.substring(6, 8), 16);
        return ret;
    }

    public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {

        // Should we do mime type checks?

        // note: not using getImageBuffer() here cause this method is not called during seeding, so
        // there's no gain
        Resource buffer = new ByteArrayResource(2048);
        sourceHelper.makeRequest(tile, buffer);
        tile.setBlob(buffer);

        return tile;
    }

    /**
     * 
     * @param map
     * @return {full query string with default, query string with modifiers}
     * @throws GeoWebCacheException
     */
    public String[] getModifiableParameters(Map<String, String[]> map, String encoding)
            throws GeoWebCacheException {
        String[] paramStrs = new String[2];

        if (sortedModParamsKeys == null)
            return null;

        String[] values = ServletUtils.selectedStringsFromMap(map, encoding, sortedModParamsKeys);

        StringBuilder strModifiers = new StringBuilder();
        StringBuilder strFull = new StringBuilder();

        for (int i = 0; i < values.length; i++) {
            String value = decodeDimensionValue(values[i]);
            ParameterFilter modParam = this.sortedModParams[i];

            if (value == null || value.length() == 0) {
                if (modParam.getDefaultValue() != null) {
                    strFull.append("&").append(modParam.getKey()).append("=")
                            .append(modParam.getDefaultValue());
                }
                // Otherwise we just omit it
            } else {
                String filteredValue = ServletUtils.URLEncode(modParam.apply(value));
                strModifiers.append("&").append(modParam.getKey()).append("=")
                        .append(filteredValue);
            }
        }

        paramStrs[1] = strModifiers.toString();
        paramStrs[0] = strFull.append(strModifiers).toString();

        return paramStrs;
    }

    public void mergeWith(WMSLayer otherLayer) throws GeoWebCacheException {
        if (otherLayer.parameterFilters != null) {
            if (this.parameterFilters != null) {
                Iterator<ParameterFilter> iter = otherLayer.parameterFilters.iterator();
                while (iter.hasNext()) {
                    this.parameterFilters.add(iter.next());
                }
            } else {
                this.parameterFilters = otherLayer.parameterFilters;
            }
        }
    }

    public String backendSRSOverride(SRS srs) {
        if (sphericalMercatorOverride != null && srs.equals(SRS.getEPSG3857())) {
            return sphericalMercatorOverride;
        } else {
            return srs.toString();
        }
    }

    private Object readResolve() {
        // Not really needed at this point
        return this;
    }

    public static String decodeDimensionValue(String value) {
        if (value != null && value.startsWith("_")) {
            if (value.equals("_null")) {
                return null;
            } else if (value.equals("_empty")) {
                return "";
            } else {
                return value;
            }
        } else {
            return value;
        }
    }

    public static String encodeDimensionValue(String value) {
        if (value == null) {
            return "_null";
        } else if (value.length() == 0) {
            return "_empty";
        } else {
            return value;
        }
    }

    public void cleanUpThreadLocals() {
        WMS_BUFFER.remove();
        WMS_BUFFER2.remove();
    }
}
