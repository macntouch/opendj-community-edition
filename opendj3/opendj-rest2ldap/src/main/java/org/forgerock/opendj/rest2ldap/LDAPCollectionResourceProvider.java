/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2012-2013 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Utils.accumulate;
import static org.forgerock.opendj.rest2ldap.Utils.transform;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryFilter;
import org.forgerock.json.resource.QueryFilterVisitor;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResult;
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.json.resource.UncategorizedException;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.opendj.ldap.AssertionFailureException;
import org.forgerock.opendj.ldap.AuthenticationException;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionException;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Function;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.TimeoutResultException;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;

/**
 * A {@code CollectionResourceProvider} implementation which maps a JSON
 * resource collection to LDAP entries beneath a base DN.
 */
public class LDAPCollectionResourceProvider implements CollectionResourceProvider {

    private abstract class AbstractRequestCompletionHandler<R,
            H extends org.forgerock.opendj.ldap.ResultHandler<? super R>>
            implements org.forgerock.opendj.ldap.ResultHandler<R> {
        final Connection connection;
        final H resultHandler;

        AbstractRequestCompletionHandler(final Connection connection, final H resultHandler) {
            this.connection = connection;
            this.resultHandler = resultHandler;
        }

        @Override
        public final void handleErrorResult(final ErrorResultException error) {
            connection.close();
            resultHandler.handleErrorResult(error);
        }

        @Override
        public final void handleResult(final R result) {
            connection.close();
            resultHandler.handleResult(result);
        }

    }

    private abstract class ConnectionCompletionHandler<R> implements
            org.forgerock.opendj.ldap.ResultHandler<Connection> {
        private final org.forgerock.opendj.ldap.ResultHandler<? super R> resultHandler;

        ConnectionCompletionHandler(
                final org.forgerock.opendj.ldap.ResultHandler<? super R> resultHandler) {
            this.resultHandler = resultHandler;
        }

        @Override
        public final void handleErrorResult(final ErrorResultException error) {
            resultHandler.handleErrorResult(error);
        }

        @Override
        public abstract void handleResult(Connection connection);

    }

    private final class RequestCompletionHandler<R> extends
            AbstractRequestCompletionHandler<R, org.forgerock.opendj.ldap.ResultHandler<? super R>> {
        RequestCompletionHandler(final Connection connection,
                final org.forgerock.opendj.ldap.ResultHandler<? super R> resultHandler) {
            super(connection, resultHandler);
        }
    }

    private final class SearchRequestCompletionHandler extends
            AbstractRequestCompletionHandler<Result, SearchResultHandler> implements
            SearchResultHandler {

        SearchRequestCompletionHandler(final Connection connection,
                final SearchResultHandler resultHandler) {
            super(connection, resultHandler);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final boolean handleEntry(final SearchResultEntry entry) {
            return resultHandler.handleEntry(entry);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final boolean handleReference(final SearchResultReference reference) {
            return resultHandler.handleReference(reference);
        }

    }

    // FIXME: make this configurable.
    private static final String ETAG_ATTRIBUTE = "etag";

    // Dummy exception used for signalling search success.
    private static final ResourceException SUCCESS = new UncategorizedException(0, null, null);

    // FIXME: make this configurable, also allow use of DN.
    private static final String UUID_ATTRIBUTE = "entryUUID";

    private final AttributeMapper attributeMapper;
    private final DN baseDN; // TODO: support template variables.
    private final Config config;
    private final ConnectionFactory factory;

    /**
     * Creates a new LDAP resource.
     *
     * @param baseDN
     *            The parent of all entries contained in this LDAP collection.
     * @param mapper
     *            The attribute mapper which will be used for mapping LDAP
     *            attributes to JSON attributes.
     * @param factory
     *            The LDAP connection factory which will be used for performing
     *            LDAP operations.
     * @param config
     *            Common configuration options.
     */
    public LDAPCollectionResourceProvider(final DN baseDN, final AttributeMapper mapper,
            final ConnectionFactory factory, final Config config) {
        this.baseDN = baseDN;
        this.attributeMapper = mapper;
        this.factory = factory;
        this.config = config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionCollection(final ServerContext context, final ActionRequest request,
            final ResultHandler<JsonValue> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionInstance(final ServerContext context, final String resourceId,
            final ActionRequest request, final ResultHandler<JsonValue> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createInstance(final ServerContext context, final CreateRequest request,
            final ResultHandler<Resource> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteInstance(final ServerContext context, final String resourceId,
            final DeleteRequest request, final ResultHandler<Resource> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patchInstance(final ServerContext context, final String resourceId,
            final PatchRequest request, final ResultHandler<Resource> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void queryCollection(final ServerContext context, final QueryRequest request,
            final QueryResultHandler handler) {
        final Context c = wrap(context);
        final Collection<String> ldapAttributes = getLDAPAttributes(c, request.getFieldFilters());

        // The handler which will be invoked for each LDAP search result.
        final SearchResultHandler searchHandler = new SearchResultHandler() {
            private final AtomicInteger pendingResourceCount = new AtomicInteger();
            private final AtomicReference<ResourceException> pendingResult =
                    new AtomicReference<ResourceException>();
            private final AtomicBoolean resultSent = new AtomicBoolean();

            @Override
            public boolean handleEntry(final SearchResultEntry entry) {
                /*
                 * Search result entries will be returned before the search
                 * result/error so the only reason pendingResult will be
                 * non-null is if a mapping error has occurred.
                 */
                if (pendingResult.get() != null) {
                    return false;
                }

                // TODO: should the resource or the container define the ID
                // mapping?
                final String id = getIDFromEntry(entry);
                final String revision = getEtagFromEntry(entry);
                final ResultHandler<Map<String, Object>> mapHandler =
                        new ResultHandler<Map<String, Object>>() {
                            @Override
                            public void handleError(final ResourceException e) {
                                pendingResult.compareAndSet(null, e);
                                pendingResourceCount.decrementAndGet();
                                completeIfNecessary();
                            }

                            @Override
                            public void handleResult(final Map<String, Object> result) {
                                final Resource resource =
                                        new Resource(id, revision, new JsonValue(result));
                                handler.handleResource(resource);
                                pendingResourceCount.decrementAndGet();
                                completeIfNecessary();
                            }
                        };

                pendingResourceCount.incrementAndGet();
                attributeMapper.toJSON(c, entry, mapHandler);
                return true;
            }

            @Override
            public void handleErrorResult(final ErrorResultException error) {
                pendingResult.compareAndSet(null, adaptErrorResult(error));
                completeIfNecessary();
            }

            @Override
            public boolean handleReference(final SearchResultReference reference) {
                // TODO: should this be classed as an error since rest2ldap
                // assumes entries are all colocated?
                return true;
            }

            @Override
            public void handleResult(final Result result) {
                pendingResult.compareAndSet(null, SUCCESS);
                completeIfNecessary();
            }

            /*
             * Close out the query result set if there are no more pending
             * resources and the LDAP result has been received.
             */
            private void completeIfNecessary() {
                if (pendingResourceCount.get() == 0) {
                    final ResourceException result = pendingResult.get();
                    if (result != null && resultSent.compareAndSet(false, true)) {
                        if (result == SUCCESS) {
                            handler.handleResult(new QueryResult());
                        } else {
                            handler.handleError(result);
                        }
                    }
                }
            }
        };

        // The handler which will be invoked once the LDAP filter has been transformed.
        final ResultHandler<Filter> filterHandler = new ResultHandler<Filter>() {
            @Override
            public void handleError(final ResourceException error) {
                handler.handleError(error);
            }

            @Override
            public void handleResult(final Filter ldapFilter) {
                // Avoid performing a search if the filter could not be mapped or if it will never match.
                if (ldapFilter == null || ldapFilter == c.getConfig().falseFilter()) {
                    handler.handleResult(new QueryResult());
                } else {
                    final String[] tmp = getSearchAttributes(ldapAttributes);
                    final ConnectionCompletionHandler<Result> outerHandler =
                            new ConnectionCompletionHandler<Result>(searchHandler) {

                                @Override
                                public void handleResult(final Connection connection) {
                                    final SearchRequestCompletionHandler innerHandler =
                                            new SearchRequestCompletionHandler(connection,
                                                    searchHandler);
                                    final SearchRequest request =
                                            Requests.newSearchRequest(baseDN,
                                                    SearchScope.SINGLE_LEVEL, ldapFilter, tmp);
                                    connection.searchAsync(request, null, innerHandler);
                                }

                            };

                    factory.getConnectionAsync(outerHandler);
                }
            }
        };

        getLDAPFilter(c, request.getQueryFilter(), filterHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readInstance(final ServerContext context, final String resourceId,
            final ReadRequest request, final ResultHandler<Resource> handler) {
        final Context c = wrap(context);
        final Collection<String> ldapAttributes = getLDAPAttributes(c, request.getFieldFilters());
        final String[] tmp = getSearchAttributes(ldapAttributes);

        // The handler which will be invoked for the LDAP search result.
        final org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry> searchHandler =
                new org.forgerock.opendj.ldap.ResultHandler<SearchResultEntry>() {
                    @Override
                    public void handleErrorResult(final ErrorResultException error) {
                        handler.handleError(adaptErrorResult(error));
                    }

                    @Override
                    public void handleResult(final SearchResultEntry entry) {
                        final String revision = getEtagFromEntry(entry);
                        final ResultHandler<Map<String, Object>> mapHandler =
                                new ResultHandler<Map<String, Object>>() {
                                    @Override
                                    public void handleError(final ResourceException e) {
                                        handler.handleError(e);
                                    }

                                    @Override
                                    public void handleResult(final Map<String, Object> result) {
                                        final Resource resource =
                                                new Resource(resourceId, revision, new JsonValue(
                                                        result));
                                        handler.handleResult(resource);
                                    }
                                };
                        attributeMapper.toJSON(c, entry, mapHandler);
                    }
                };

        // The handler which will be invoked
        final ConnectionCompletionHandler<SearchResultEntry> outerHandler =
                new ConnectionCompletionHandler<SearchResultEntry>(searchHandler) {

                    @Override
                    public void handleResult(final Connection connection) {
                        final RequestCompletionHandler<SearchResultEntry> innerHandler =
                                new RequestCompletionHandler<SearchResultEntry>(connection,
                                        searchHandler);
                        final SearchRequest request =
                                Requests.newSearchRequest(baseDN, SearchScope.SINGLE_LEVEL, Filter
                                        .equality(UUID_ATTRIBUTE, resourceId), tmp);
                        connection.searchSingleEntryAsync(request, innerHandler);
                    }

                };

        factory.getConnectionAsync(outerHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateInstance(final ServerContext context, final String resourceId,
            final UpdateRequest request, final ResultHandler<Resource> handler) {
        handler.handleError(new NotSupportedException("Not yet implemented"));
    }

    /**
     * Adapts an LDAP result code to a resource exception.
     *
     * @param error
     *            The LDAP error that should be adapted.
     * @return The equivalent resource exception.
     */
    private ResourceException adaptErrorResult(final ErrorResultException error) {
        int resourceResultCode;
        try {
            throw error;
        } catch (final AssertionFailureException e) {
            resourceResultCode = ResourceException.VERSION_MISMATCH;
        } catch (final AuthenticationException e) {
            resourceResultCode = 401;
        } catch (final AuthorizationException e) {
            resourceResultCode = ResourceException.FORBIDDEN;
        } catch (final ConnectionException e) {
            resourceResultCode = ResourceException.UNAVAILABLE;
        } catch (final EntryNotFoundException e) {
            resourceResultCode = ResourceException.NOT_FOUND;
        } catch (final MultipleEntriesFoundException e) {
            resourceResultCode = ResourceException.INTERNAL_ERROR;
        } catch (final TimeoutResultException e) {
            resourceResultCode = 408;
        } catch (final ErrorResultException e) {
            resourceResultCode = ResourceException.INTERNAL_ERROR;
        }
        return ResourceException.getException(resourceResultCode, null, error.getMessage(), error);
    }

    /**
     * Returns the ETag for the provided entry.
     *
     * @param entry
     *            The entry.
     * @return The ETag.
     */
    private String getEtagFromEntry(final Entry entry) {
        return entry.parseAttribute(ETAG_ATTRIBUTE).asString();
    }

    /**
     * Returns the resource ID for the provided entry.
     *
     * @param entry
     *            The entry.
     * @return The resource ID.
     */
    private String getIDFromEntry(final Entry entry) {
        return entry.parseAttribute(UUID_ATTRIBUTE).asString();
    }

    /**
     * Determines the set of LDAP attributes to request in an LDAP read (search,
     * post-read), based on the provided list of JSON pointers.
     *
     * @param requestedAttributes
     *            The list of resource attributes to be read.
     * @return The set of LDAP attributes associated with the resource
     *         attributes.
     */
    private Collection<String> getLDAPAttributes(final Context c,
            final Collection<JsonPointer> requestedAttributes) {
        final Set<String> requestedLDAPAttributes;
        if (requestedAttributes.isEmpty()) {
            // Full read.
            requestedLDAPAttributes = new LinkedHashSet<String>();
            attributeMapper.getLDAPAttributes(c, new JsonPointer(), requestedLDAPAttributes);
        } else {
            // Partial read.
            requestedLDAPAttributes = new LinkedHashSet<String>(requestedAttributes.size());
            for (final JsonPointer requestedAttribute : requestedAttributes) {
                attributeMapper.getLDAPAttributes(c, requestedAttribute, requestedLDAPAttributes);
            }
        }
        return requestedLDAPAttributes;
    }

    private void getLDAPFilter(final Context c, final QueryFilter queryFilter,
            final ResultHandler<Filter> h) {
        final QueryFilterVisitor<Void, ResultHandler<Filter>> visitor =
                new QueryFilterVisitor<Void, ResultHandler<Filter>>() {
                    @Override
                    public Void visitAndFilter(final ResultHandler<Filter> p,
                            final List<QueryFilter> subFilters) {
                        final ResultHandler<Filter> handler =
                                accumulate(subFilters.size(), transform(
                                        new Function<List<Filter>, Filter, Void>() {
                                            @Override
                                            public Filter apply(final List<Filter> value,
                                                    final Void p) {
                                                // Check for unmapped filter components and optimize.
                                                final Iterator<Filter> i = value.iterator();
                                                while (i.hasNext()) {
                                                    final Filter f = i.next();
                                                    if (f == null) {
                                                        // Filter component did not match any attribute mappers.
                                                        return c.getConfig().falseFilter();
                                                    } else if (f == c.getConfig().falseFilter()) {
                                                        return c.getConfig().falseFilter();
                                                    } else if (f == c.getConfig().trueFilter()) {
                                                        i.remove();
                                                    }
                                                }
                                                switch (value.size()) {
                                                case 0:
                                                    return c.getConfig().trueFilter();
                                                case 1:
                                                    return value.get(0);
                                                default:
                                                    return Filter.and(value);
                                                }
                                            }
                                        }, p));
                        for (final QueryFilter subFilter : subFilters) {
                            subFilter.accept(this, handler);
                        }
                        return null;
                    }

                    @Override
                    public Void visitBooleanLiteralFilter(final ResultHandler<Filter> p,
                            final boolean value) {
                        p.handleResult(value ? c.getConfig().trueFilter() : c.getConfig()
                                .falseFilter());
                        return null;
                    }

                    @Override
                    public Void visitContainsFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.CONTAINS, field, null,
                                valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitEqualsFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.EQUAL_TO, field, null,
                                valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitExtendedMatchFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final String operator,
                            final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.EXTENDED, field, operator,
                                valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitGreaterThanFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.GREATER_THAN, field, null,
                                valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitGreaterThanOrEqualToFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.GREATER_THAN_OR_EQUAL_TO,
                                field, null, valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitLessThanFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.LESS_THAN, field, null,
                                valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitLessThanOrEqualToFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.LESS_THAN_OR_EQUAL_TO, field,
                                null, valueAssertion, p);
                        return null;
                    }

                    @Override
                    public Void visitNotFilter(final ResultHandler<Filter> p,
                            final QueryFilter subFilter) {
                        subFilter.accept(this, transform(new Function<Filter, Filter, Void>() {
                            @Override
                            public Filter apply(final Filter value, final Void p) {
                                if (value == null) {
                                    // Filter component did not match any attribute mappers.
                                    return c.getConfig().trueFilter();
                                } else if (value == c.getConfig().falseFilter()) {
                                    return c.getConfig().trueFilter();
                                } else if (value == c.getConfig().trueFilter()) {
                                    return c.getConfig().falseFilter();
                                } else {
                                    return Filter.not(value);
                                }
                            }
                        }, p));
                        return null;
                    }

                    @Override
                    public Void visitOrFilter(final ResultHandler<Filter> p,
                            final List<QueryFilter> subFilters) {
                        final ResultHandler<Filter> handler =
                                accumulate(subFilters.size(), transform(
                                        new Function<List<Filter>, Filter, Void>() {
                                            @Override
                                            public Filter apply(final List<Filter> value,
                                                    final Void p) {
                                                // Check for unmapped filter components and optimize.
                                                final Iterator<Filter> i = value.iterator();
                                                while (i.hasNext()) {
                                                    final Filter f = i.next();
                                                    if (f == null) {
                                                        // Filter component did not match any attribute mappers.
                                                        i.remove();
                                                    } else if (f == c.getConfig().falseFilter()) {
                                                        i.remove();
                                                    } else if (f == c.getConfig().trueFilter()) {
                                                        return c.getConfig().trueFilter();
                                                    }
                                                }
                                                switch (value.size()) {
                                                case 0:
                                                    return c.getConfig().falseFilter();
                                                case 1:
                                                    return value.get(0);
                                                default:
                                                    return Filter.or(value);
                                                }
                                            }
                                        }, p));
                        for (final QueryFilter subFilter : subFilters) {
                            subFilter.accept(this, handler);
                        }
                        return null;
                    }

                    @Override
                    public Void visitPresentFilter(final ResultHandler<Filter> p,
                            final JsonPointer field) {
                        attributeMapper.getLDAPFilter(c, FilterType.PRESENT, field, null, null, p);
                        return null;
                    }

                    @Override
                    public Void visitStartsWithFilter(final ResultHandler<Filter> p,
                            final JsonPointer field, final Object valueAssertion) {
                        attributeMapper.getLDAPFilter(c, FilterType.STARTS_WITH, field, null,
                                valueAssertion, p);
                        return null;
                    }

                };
        // Note that the returned LDAP filter may be null if it could not be mapped by any attribute mappers.
        queryFilter.accept(visitor, h);
    }

    private String[] getSearchAttributes(final Collection<String> attributes) {
        // FIXME: who is responsible for adding the UUID and etag attributes to
        // this search?
        final String[] tmp = attributes.toArray(new String[attributes.size() + 2]);
        tmp[tmp.length - 2] = UUID_ATTRIBUTE;
        tmp[tmp.length - 1] = ETAG_ATTRIBUTE;
        return tmp;
    }

    private Context wrap(final ServerContext context) {
        return new Context(config, context);
    }
}