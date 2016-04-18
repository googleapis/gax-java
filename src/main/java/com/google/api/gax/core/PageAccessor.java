package com.google.api.gax.core;

/**
 * Accessor for paged results from a list API method
 *
 * <p>This is a subclass of Iterable where iterator() returns an Iterator object with the complete
 * listing result. If necessary it can perform more rpc calls to fetch more pages.
 */
public interface PageAccessor<T> extends Iterable<T> {
  /**
   * Returns the values contained in this page.
   * Note: This method is not thread-safe.
   */
  Iterable<T> getPageValues();

  /**
   * Returns the next page of results or {@code null} if no more results.
   * Note: This method is not thread-safe.
   */
  PageAccessor<T> getNextPage();

  /**
   * Returns the token for the next page or {@code null} if no more results.
   * Note: This method is not thread-safe.
   */
  String getNextPageToken();
}
