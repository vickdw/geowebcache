package org.geowebcache.rest.controller;

import org.geowebcache.grid.GridSubset;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.rest.exception.RestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@Component
@RestController
public class BoundsController extends GWCController {
    @Autowired
    TileLayerDispatcher tld;

    @ExceptionHandler(RestException.class)
    public ResponseEntity<?> handleRestException(RestException ex) {
        return new ResponseEntity<Object>(ex.toString(), ex.getStatus());
    }

    @RequestMapping(value = "/bounds/{layer}/{srs}/{type}", method = RequestMethod.GET)
    public ResponseEntity<?> doGet(HttpServletRequest request,
                                   @PathVariable String layer,
                                   @PathVariable String srs,
                                   @PathVariable String type) {
        TileLayer tl = findTileLayer(layer, tld);
        if(tl == null) {
            throw new RestException(layer + " is not known", HttpStatus.NOT_FOUND);
        }

        GridSubset grid = tl.getGridSubset(srs);

        if(grid == null) {
            throw new RestException(layer + " does not support " + srs, HttpStatus.NOT_FOUND);
        }



        StringBuilder str = new StringBuilder();
        long[][] bounds = grid.getCoverages();

        if(type.equalsIgnoreCase("java")) {
            str.append("{");
            for (int i = 0; i < bounds.length; i++) {
                str.append("{");

                for (int j = 0; j < bounds[i].length; j++) {
                    str.append(bounds[i][j]);

                    if (j + 1 < bounds[i].length) {
                        str.append(", ");
                    }
                }

                str.append("}");

                if (i + 1 < bounds.length) {
                    str.append(", ");
                }
            }
            str.append("}");

            return new ResponseEntity<Object>(str.toString(), HttpStatus.OK);
        } else {
            throw new RestException("Unknown or missing format extension : " + type,
                    HttpStatus.BAD_REQUEST);
        }
    }
    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        tld = tileLayerDispatcher;
    }
}
