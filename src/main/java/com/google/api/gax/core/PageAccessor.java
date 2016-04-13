package com.google.api.gax.core;

/**
 * Accessor for paged results from a list API method"
 */
public interface PageAccessor<T> extends Iterable<T> {
  /**
   * Returns the values contained in this page.
   */
  Iterable<T> getPageValues();

  /**
   * Returns the next page of results or {@code null} if no more results.
   */
  PageAccessor<T> getNextPage();

  /**
   * Returns the token for the next page or {@code null} if no more results.
   */
  String getNextPageToken();
}
