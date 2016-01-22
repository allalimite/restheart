/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.documents;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.List;
import org.bson.types.ObjectId;
import org.restheart.Configuration;
import org.restheart.hal.AbstractRepresentationFactory;
import org.restheart.hal.Link;
import org.restheart.hal.Representation;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.hal.metadata.RequestChecker;
import org.restheart.hal.metadata.singletons.JsonSchemaChecker;
import org.restheart.handlers.IllegalQueryParamenterException;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.TYPE;
import org.restheart.handlers.document.DocumentRepresentationFactory;
import org.restheart.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DocumentsRepresentationFactory extends AbstractRepresentationFactory {

    public DocumentsRepresentationFactory() {
    }

    /**
     *
     * @param exchange
     * @param context
     * @param documents
     * @param size
     * @return
     * @throws IllegalQueryParamenterException
     */
    @Override
    public Representation getRepresentation(HttpServerExchange exchange, RequestContext context, List<DBObject> documents, long size)
            throws IllegalQueryParamenterException {
        final String requestPath = buildRequestPath(exchange);
        final Representation rep = createRepresentation(exchange, context, requestPath);

        addSizeAndTotalPagesProperties(size, context, rep);

        addDocuments(documents, rep, requestPath, exchange, context);

        if (context.isFullHalMode()) {

            addSpecialProperties(rep, context.getType(), context.getCollectionProps());

            addPaginationLinks(exchange, context, size, rep);

            addLinkTemplates(context, rep, requestPath);

            // curies
            rep.addLink(new Link("rh", "curies", Configuration.RESTHEART_ONLINE_DOC_URL
                    + "/{rel}.html", true), true);
        } else {
            // empty curies section. this is needed due to HAL browser issue
            // https://github.com/mikekelly/hal-browser/issues/71
            rep.addLinkArray("curies");
        }

        return rep;
    }

    public static void addSpecialProperties(final Representation rep,
            final RequestContext.TYPE type,
            final DBObject data) {
        rep.addProperty("_type", type.name());

        Object etag = data.get("_etag");

        if (etag != null && etag instanceof ObjectId) {
            if (data.get("_lastupdated_on") == null) {
                // add the _lastupdated_on in case the _etag field is present and its value is an ObjectId
                rep.addProperty("_lastupdated_on", Instant.ofEpochSecond(((ObjectId) etag).getTimestamp()).toString());
            }
        }
    }

    private void addDocuments(List<DBObject> documents, final Representation rep, final String requestPath, final HttpServerExchange exchange, final RequestContext context)
            throws IllegalQueryParamenterException {
        if (documents != null) {
            addReturnedProperty(documents, rep);

            if (!documents.isEmpty()) {
                addDocuments(documents, requestPath, exchange, context, rep);
            }
        } else {
            rep.addProperty("_returned", 0);
        }
    }

    private void addLinkTemplates(final RequestContext context, final Representation rep, final String requestPath) {
        // link templates and curies
        if (context.isParentAccessible()) {
            // this can happen due to mongo-mounts mapped URL
            rep.addLink(new Link("rh:db", URLUtils.getParentPath(requestPath)));
        }

        if (TYPE.FILES_BUCKET.equals(context.getType())) {
            rep.addLink(new Link("rh:bucket", URLUtils.getParentPath(requestPath) + "/{bucketname}" + RequestContext.FS_FILES_SUFFIX, true));
            rep.addLink(new Link("rh:file", requestPath + "/{fileid}{?id_type}", true));
        } else if (TYPE.COLLECTION.equals(context.getType())) {
            rep.addLink(new Link("rh:coll", URLUtils.getParentPath(requestPath) + "/{collname}", true));
            rep.addLink(new Link("rh:document", requestPath + "/{docid}{?id_type}", true));
        }

        rep.addLink(new Link("rh:filter", requestPath + "{?filter}", true));
        rep.addLink(new Link("rh:sort", requestPath + "{?sort_by}", true));
        rep.addLink(new Link("rh:paging", requestPath + "{?page}{&pagesize}", true));
    }

    private void addDocuments(List<DBObject> documents, String requestPath, HttpServerExchange exchange, RequestContext context, Representation rep) throws IllegalQueryParamenterException {
        for (DBObject d : documents) {
            Object _id = d.get("_id");

            if (RequestContext.isReservedResourceCollection(_id.toString())) {
                rep.addWarning("filtered out reserved resource " + requestPath + "/" + _id.toString());
            } else {
                Representation nrep = new DocumentRepresentationFactory().getRepresentation(requestPath + "/" + _id.toString(), exchange, context, d);

                if (context.getType() == RequestContext.TYPE.FILES_BUCKET) {
                    if (context.isFullHalMode()) {
                        DocumentRepresentationFactory.addSpecialProperties(nrep, TYPE.FILE, d);
                    }

                    rep.addRepresentation("rh:file", nrep);
                } else if (context.getType() == RequestContext.TYPE.SCHEMA_STORE) {
                    if (context.isFullHalMode()) {
                        DocumentRepresentationFactory.addSpecialProperties(nrep, TYPE.SCHEMA, d);
                    }

                    rep.addRepresentation("rh:schema", nrep);
                    
                } else {
                    if (context.isFullHalMode()) {
                        DocumentRepresentationFactory.addSpecialProperties(nrep, TYPE.DOCUMENT, d);
                    }

                    rep.addRepresentation("rh:doc", nrep);
                }
            }
        }
    }
}