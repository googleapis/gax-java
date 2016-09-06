/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.api.gax.core;

/**
 * A Page object wraps an API list method response.
 *
 * <p>Callers can iterate over the Page object to get all elements returned in the page. The Page
 * object also provides methods to retrieve additional pages using the page token, and to get the
 * API request and response objects.
 */
public interface Page<RequestT, ResponseT, ResourceT> extends Iterable<ResourceT> {
  /**
   * Returns true if there are more pages that can be retrieved from the API.
   */
  boolean hasNextPage();

  /**
   * Returns the next page token from the response.
   */
  Object getNextPageToken();

  /**
   * Retrieves the next Page object using the next page token. If there are no more pages to be
   * retrieved, a NoSuchElementException is thrown. The hasNextPage() method should be used to check
   * if a Page object is available.
   */
  Page<RequestT, ResponseT, ResourceT> getNextPage();

  /**
   * Retrieves the next Page object using the next page token. Uses the pageSize argument to set the
   * page size parameter for the next page request. If there are no more pages to be retrieved, a
   * NoSuchElementException is thrown. The hasNextPage() method should be used to check if a Page
   * object is available.
   */
  Page<RequestT, ResponseT, ResourceT> getNextPage(int pageSize);

  /**
   * Return the number of elements in the response.
   */
  int getPageElementCount();

  /**
   * Return an iterator over Page objects, beginning with this object. Additional Page objects are
   * retrieved lazily via API calls until all elements have been retrieved.
   */
  Iterable<Page<RequestT, ResponseT, ResourceT>> iteratePages();

  /**
   * Gets the request object used to generate the Page.
   */
  RequestT getRequestObject();

  /**
   * Gets the API response object.
   */
  ResponseT getResponseObject();
}
