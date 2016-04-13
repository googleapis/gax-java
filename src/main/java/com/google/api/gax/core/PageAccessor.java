package com.google.api.gax.core;

public interface PageAccessor<T> extends Iterable<T> {
  /**
   * Returns the values contained in this page.
   */
  Iterable<T> getPageValues();

  /**
   * Returns the next page of results or {@code null} if no more result.
   */
  PageAccessor<T> getNextPage();

  /**
   * Returns the token for the next page or {@code null} if no more results.
   */
  String getNextPageToken();
}
