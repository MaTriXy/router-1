package com.eyeem.router;

/*
    Routable for Android

    Copyright (c) 2013 Turboprop, Inc. <clay@usepropeller.com> http://usepropeller.com
    Copyright (c) 2016 EyeEm Mobile GmbH

    Licensed under the MIT License.

    Permission is hereby granted, free of charge, to any person obtaining a copy
	of this software and associated documentation files (the "Software"), to deal
	in the Software without restriction, including without limitation the rights
	to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
	copies of the Software, and to permit persons to whom the Software is
	furnished to do so, subject to the following conditions:

	The above copyright notice and this permission notice shall be included in
	all copies or substantial portions of the Software.

	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
	THE SOFTWARE.
*/


import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.eyeem.router.RouterUtils.cleanUrl;
import static com.eyeem.router.RouterUtils.isWildcard;

public class Router {

   public final static String ROUTER_SERVICE = Router.class.getCanonicalName();

   /**
    * Default params shared across all router paths
    */
   private HashMap<String, Object> globalParams = new HashMap<>();

   public static Router from(Context context) {
      //noinspection WrongConstant
      return (Router) context.getSystemService(ROUTER_SERVICE);
   }

   /**
    * The class used when you want to map a function (given in `run`)
    * to a Router URL.
    */
   public static abstract class BundleBuilder {
      public abstract Bundle bundleFor(RouteContext context);
   }

   /**
    * The class supplied to custom callbacks to describe the route route
    */
   public class RouteContext {
      Map<String, String> _params;
      Bundle _extras;
      Context _context;
      String _url;

      public RouteContext(Map<String, String> params, Bundle extras, Context context, String url) {
         _params = params;
         _extras = extras;
         _context = context;
         _url = url;
      }

      /**
       * Returns the route parameters as specified by the configured route
       */
      public Map<String, String> getParams() {
         return _params;
      }

      /**
       * Returns the extras supplied with the route
       */
      public Bundle getExtras() {
         return _extras;
      }

      /**
       * Returns the Android Context that should be used to open the route
       */
      public Context getContext() {
         return _context;
      }

      /**
       * Returns the url that is being resolved by the router
       */
      public String url() {
         return _url;
      }
   }

   /**
    * The class used to determine behavior when opening a URL.
    * If you want to extend Routable to handle things like transition
    * animations or fragments, this class should be augmented.
    */
   public static class RouterOptions {
      BundleBuilder _bundleBuilder;
      Map<String, String> _defaultParams;

      public RouterOptions() {}

      public RouterOptions(Map<String, String> defaultParams) {
         this.setDefaultParams(defaultParams);
      }

      public BundleBuilder getBundleBuilder() {
         return this._bundleBuilder;
      }

      public void setBundleBuilder(BundleBuilder callback) {
         this._bundleBuilder = callback;
      }

      public void setDefaultParams(Map<String, String> defaultParams) {
         this._defaultParams = defaultParams;
      }

      public Map<String, String> getDefaultParams() {
         return this._defaultParams;
      }
   }

   private static class RouterParams {
      public RouterOptions routerOptions;
      public Map<String, String> openParams;
   }

   private final Map<String, RouterOptions> _routes = new LinkedHashMap<>();
   private final Map<String, RouterOptions> _wildcardRoutes = new LinkedHashMap<>();
   private String _rootUrl = null;
   private final Map<String, RouterParams> _cachedRoutes = new HashMap<>();
   private Context _context;

   /**
    * Creates a new Router
    */
   public Router() {

   }

   /**
    * Creates a new Router
    *
    * @param context {@link Context} that all {@link Intent}s generated by the router will use
    */
   public Router(Context context) {
      this.setContext(context);
   }

   /**
    * @param context {@link Context} that all {@link Intent}s generated by the router will use
    */
   public void setContext(Context context) {
      this._context = context;
   }

   /**
    * @return The context for the router
    */
   public Context getContext() {
      return this._context;
   }

   /**
    * Map a URL to a callback
    *
    * @param format   The URL being mapped; for example, "users/:id" or "groups/:id/topics/:topic_id"
    * @param callback {@link BundleBuilder} instance which contains the code to execute when the URL is opened
    */
   public void map(String format, BundleBuilder callback) {
      RouterOptions options = new RouterOptions();
      options.setBundleBuilder(callback);
      this.map(format, options);
   }

   /**
    * Map a URL to {@link RouterOptions}
    *
    * @param format  The URL being mapped; for example, "users/:id" or "groups/:id/topics/:topic_id"
    * @param options The {@link RouterOptions} to be used for more granular and customized options for when the URL is opened
    */
   public void map(String format, RouterOptions options) {
      if (options == null) {
         options = new RouterOptions();
      }

      if (isWildcard(format)) {
         this._wildcardRoutes.put(format, options);
      } else {
         this._routes.put(format, options);
      }
   }

   /**
    * Set the root url; used when opening an activity or callback via RouterActivity
    *
    * @param rootUrl The URL format to use as the root
    */
   public void setRootUrl(String rootUrl) {
      this._rootUrl = rootUrl;
   }

   /**
    * @return The router's root URL, or null.
    */
   public String getRootUrl() {
      return this._rootUrl;
   }

   /**
    * Open a map'd URL set using {@link #map(String, Class)} or {@link #map(String, BundleBuilder)}
    *
    * @param url The URL; for example, "users/16" or "groups/5/topics/20"
    */
   public Bundle bundleFor(String url) {
      return this.bundleFor(url, this._context);
   }

   /**
    * Open a map'd URL set using {@link #map(String, Class)} or {@link #map(String, BundleBuilder)}
    *
    * @param url    The URL; for example, "users/16" or "groups/5/topics/20"
    * @param extras The {@link Bundle} which contains the extras to be assigned to the generated {@link Intent}
    */
   public Bundle bundleFor(String url, Bundle extras) {
      return this.bundleFor(url, extras, this._context);
   }

   /**
    * Open a map'd URL set using {@link #map(String, Class)} or {@link #map(String, BundleBuilder)}
    *
    * @param url     The URL; for example, "users/16" or "groups/5/topics/20"
    * @param context The context which is used in the generated {@link Intent}
    */
   public Bundle bundleFor(String url, Context context) {
      return this.bundleFor(url, null, context);
   }

   /**
    * Open a map'd URL set using {@link #map(String, Class)} or {@link #map(String, BundleBuilder)}
    *
    * @param url     The URL; for example, "users/16" or "groups/5/topics/20"
    * @param extras  The {@link Bundle} which contains the extras to be assigned to the generated {@link Intent}
    * @param context The context which is used in the generated {@link Intent}
    */
   public Bundle bundleFor(String url, Bundle extras, Context context) {
      RouterParams params = this.paramsForUrl(url);
      RouterOptions options = params.routerOptions;
      if (options.getBundleBuilder() != null) {

         Map openParams = (Map)RouterLoader.copy((Serializable) params.openParams);

         // add global params to path specific params
         for (Entry<String, Object> entry : globalParams.entrySet()) {
            if (!openParams.containsKey(entry.getKey())) { // do not override locally set keys
               openParams.put(entry.getKey(), entry.getValue());
            }
         }

         RouteContext routeContext = new RouteContext(openParams, extras, context, url);

         return options.getBundleBuilder().bundleFor(routeContext);
      }

      return null;
   }

   /*
    * Takes a url (i.e. "/users/16/hello") and breaks it into a {@link RouterParams} instance where
    * each of the parameters (like ":id") has been parsed.
    */
   private RouterParams paramsForUrl(String url) {
      final String cleanedUrl = cleanUrl(url);

      Uri parsedUri = Uri.parse("http://tempuri.org/" + cleanedUrl);

      String urlPath = parsedUri.getPath().substring(1);

      if (this._cachedRoutes.get(cleanedUrl) != null) {
         return this._cachedRoutes.get(cleanedUrl);
      }

      String[] givenParts = urlPath.split("/");

      // first check for matching non wildcard routes just to avoid being shadowed
      // by more generic wildcard routes
      RouterParams routerParams = checkRouteSet(this._routes.entrySet(), givenParts, false);

      // still null, try matching to any wildcard routes
      if (routerParams == null) {
         routerParams = checkRouteSet(this._wildcardRoutes.entrySet(), givenParts, true);
      }

      if (routerParams == null) {
         throw new RouteNotFoundException("No route found for url " + url);
      }

      for (String parameterName : parsedUri.getQueryParameterNames()) {
         String parameterValue = parsedUri.getQueryParameter(parameterName);
         routerParams.openParams.put(parameterName, parameterValue);
      }

      this._cachedRoutes.put(cleanedUrl, routerParams);
      return routerParams;
   }

   private static RouterParams checkRouteSet(Set<Entry<String, RouterOptions>> routeSet, String[] givenParts, boolean isWildcard) {
      RouterParams routerParams = null;

      for (Entry<String, RouterOptions> entry : routeSet) {
         String routerUrl = cleanUrl(entry.getKey());
         RouterOptions routerOptions = entry.getValue();
         String[] routerParts = routerUrl.split("/");

         if (!isWildcard && (routerParts.length != givenParts.length)) {
            continue;
         }

         Map<String, String> givenParams = urlToParamsMap(givenParts, routerParts, isWildcard);
         if (givenParams == null) {
            continue;
         }

         routerParams = new RouterParams();
         routerParams.openParams = givenParams;
         routerParams.routerOptions = routerOptions;
         break;
      }

      return routerParams;
   }

   /**
    * @param givenUrlSegments  An array representing the URL path attempting to be opened (i.e. ["users", "42"])
    * @param routerUrlSegments An array representing a possible URL match for the router (i.e. ["users", ":id"])
    * @param hasWildcard       Tells whether there is a :wildcard: param or not
    * @return A map of URL parameters if it's a match (i.e. {"id" => "42"}) or null if there is no match
    */
   private static Map<String, String> urlToParamsMap(String[] givenUrlSegments, String[] routerUrlSegments, boolean hasWildcard) {
      Map<String, String> formatParams = new HashMap<>();
      for (
         int routerIndex = 0, givenIndex = 0;
         routerIndex < routerUrlSegments.length && givenIndex < givenUrlSegments.length;
         routerIndex++
         ) {
         String routerPart = routerUrlSegments[routerIndex];
         String givenPart = givenUrlSegments[givenIndex];

         if (routerPart.charAt(0) == ':') {
            String key = routerPart.substring(1, routerPart.length());

            // (1) region standard router behavior
            if (!hasWildcard) {
               formatParams.put(key, givenPart);
               givenIndex++;
               continue;
            }
            // endregion

            // region wildcard

            // (2) first we check if param is indeed a wildcard param
            boolean isWildcard = false;
            if (key.charAt(key.length() - 1) == ':') {
               key = key.substring(0, key.length() - 1);
               isWildcard = true;
            }

            // (3) if it's not, just do standard processing --> (1)
            if (!isWildcard) {
               formatParams.put(key, givenPart);
               givenIndex++;
               continue;
            }

            // (4) check remaining segments before consuming wildcard parameter
            String nextRouterPart = routerIndex + 1 < routerUrlSegments.length ? routerUrlSegments[routerIndex + 1] : null;

            // we need to eat everything up till next recognizable path
            // e.g. :whatever:/:id should be forbidden thus the following check
            if (!TextUtils.isEmpty(nextRouterPart) && nextRouterPart.charAt(0) == ':') {
               throw new IllegalStateException(
                  String.format(Locale.US, "Wildcard parameter %1$s cannot be directly followed by a parameter %2$s", routerPart, nextRouterPart));
            }

            // (5) all is good, it's time to eat some segments
            ArrayList<String> segments = new ArrayList<>();
            for (int i = givenIndex; i < givenUrlSegments.length; i++) {
               String tmpPart = givenUrlSegments[i];
               if (tmpPart.equals(nextRouterPart)) {
                  break;
               } else {
                  segments.add(tmpPart);
               }
            }

            // (6) put it all assembled as a wildcard param
            formatParams.put(key, TextUtils.join("/", segments));
            givenIndex += segments.size();
            continue;
            // endregion
         }

         if (!routerPart.equals(givenPart)) {
            return null;
         }
         givenIndex++; // casual increment
      }

      return formatParams;
   }

   public Router globalParam(String key, Object object) {
      globalParams.put(key, object);
      return this;
   }



   /**
    * Thrown if a given route is not found.
    */
   public static class RouteNotFoundException extends RuntimeException {
      private static final long serialVersionUID = -2278644339983544651L;

      public RouteNotFoundException(String message) {
         super(message);
      }
   }

   /**
    * Thrown if no context has been found.
    */
   public static class ContextNotProvided extends RuntimeException {
      private static final long serialVersionUID = -1381427067387547157L;

      public ContextNotProvided(String message) {
         super(message);
      }
   }

   public void clearCache() {
      _cachedRoutes.clear();
   }
}