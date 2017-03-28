/**
 *
 */
package org.alfresco.integrations.sfdc.acc;

import com.sun.istack.NotNull;
import org.alfresco.integrations.sfdc.services.CanvasService;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.social.connect.Connection;
import org.springframework.social.salesforce.api.QueryResult;
import org.springframework.social.salesforce.api.ResultItem;
import org.springframework.social.salesforce.api.Salesforce;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URLDecoder;

/**
 * Web Script to get Notes and attachments from Salesforce Account Objects
 *
 *  *
 * The core service {@link CanvasService CanvasSerivce} is loaded in a subsystem. A proxy is
 * used to make sure that it is available when the behaviour is used.
 *
 * url /sfdc/extension/getAccountNA
 *
 */
public class GetAllAccountsNotesAttachments extends DeclarativeWebScript
{
    private final static Log log = LogFactory.getLog(GetAllAccountsNotesAttachments.class);
    private CanvasService canvasService;
    private NodeService nodeService;
    private FileFolderService fileFolderService;
    private MimetypeService mimetypeService;
    private SearchService searchService;
    private List<Map> sObjectDescriptions;
    private final static String KEYPREFIX             = "keyPrefix";
    private final static String NAME                  = "name";
    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Connection<Salesforce> connection = canvasService.getConnection();
        QName contentQName = QName.createQName("{http://www.alfresco.org/model/content/1.0}content");
        QName folderQName = QName.createQName("{http://www.alfresco.org/model/content/1.0}folder");
        NodeRef destFolder = null;
        NodeRef notesAttachments;
        NodeRef existingDocument;
        Map<String, Object> model = new HashMap<>();
        String queryStr = "Select Id,(Select Name, Id, ParentId from Attachments) from Account";
        String queryNote = "Select Id,(Select Title, Id, ParentId, Body from Notes) from Account";
        QueryResult qryResult = connection.getApi().queryOperations().queryAll(queryStr);
        QueryResult resultNotes = connection.getApi().queryOperations().queryAll(queryNote);
        List<ResultItem> items = qryResult.getRecords();
        List<ResultItem> noteItems = resultNotes.getRecords();
        String documentId = "";
        FileInfo folderInfo;
        FileInfo node;
        InputStream apiResult;
        ContentWriter writer;
        String qryAccount = "";
        String parentId = "";
        String prefix = "";
        String name = "";

        if (qryResult.getTotalSize() > 0)
        {
            for (ResultItem item : items)
            {
                QueryResult results2 = (QueryResult)item.getAttributes().get("Attachments");
                if (results2 != null && !results2.getRecords().isEmpty())
                {
                    List<ResultItem> items2 = results2.getRecords();
                    for (ResultItem item2 : items2)
                    {
                        log.debug("#### Attachment " + item2.getAttributes().get("Id"));
                        documentId = item2.getAttributes().get("Id").toString();
                        parentId = item2.getAttributes().get("ParentId").toString();
                        prefix = getKeyPrefix(parentId);
                        name = getObjectType(prefix, connection);
                        if(name.equals("Account"))
                        {
                            qryAccount = "@sobjectAccount\\:Id:\"" + parentId +"\"";
                            ResultSet results = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, qryAccount);
                            log.info(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
                            log.info(qryAccount);
                            if (results != null && results.length() > 0) {
                                for (int j = 0; j < results.length(); j++) {
                                    destFolder = results.getNodeRef(j);
                                }
                            }
                        }
                        notesAttachments = nodeService.getChildByName(destFolder, ContentModel.ASSOC_CONTAINS,"Notes & Attachments");
                        if(notesAttachments == null) {
                            folderInfo = fileFolderService.create(destFolder, "Notes & Attachments", folderQName);
                            notesAttachments = folderInfo.getNodeRef();
                        }
                        existingDocument = nodeService.getChildByName(notesAttachments, ContentModel.ASSOC_CONTAINS,item2.getAttributes().get("Name").toString());
                        if(existingDocument == null)
                        {
                            node = fileFolderService.create(notesAttachments,item2.getAttributes().get("Name").toString(),contentQName);
                            apiResult = connection.getApi().sObjectsOperations().getBlob("Attachment", documentId, "Body");
                            writer = fileFolderService.getWriter(node.getNodeRef());
                            writer.setMimetype(mimetypeService.guessMimetype(node.getName()));
                            writer.putContent(apiResult);
                        }
                    }
                }
            }
        }

        if (resultNotes.getTotalSize() > 0)
        {
            for (ResultItem noteItem : noteItems)
            {
                QueryResult resultNotes2 = (QueryResult)noteItem.getAttributes().get("Notes");
                if (resultNotes2 != null && !resultNotes2.getRecords().isEmpty())
                {
                    List<ResultItem> noteItems2 = resultNotes2.getRecords();
                    for (ResultItem noteItem2 : noteItems2)
                    {
                        log.debug("#### Note " + noteItem2.getAttributes().get("Id"));
                        documentId = noteItem2.getAttributes().get("Title").toString();
                        documentId = filterName(documentId);
                        parentId = noteItem2.getAttributes().get("ParentId").toString();
                        prefix = getKeyPrefix(parentId);
                        name = getObjectType(prefix, connection);
                        if(name.equals("Account"))
                        {
                            qryAccount = "@sobjectAccount\\:Id:\"" + parentId +"\"";
                            ResultSet results = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, qryAccount);
                            log.info(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
                            log.info(qryAccount);
                            if (results != null && results.length() > 0) {
                                for (int j = 0; j < results.length(); j++) {
                                    destFolder = results.getNodeRef(j);
                                }
                            }
                        }
                        notesAttachments = nodeService.getChildByName(destFolder, ContentModel.ASSOC_CONTAINS,"Notes & Attachments");
                        if(notesAttachments == null) {
                            folderInfo = fileFolderService.create(destFolder, "Notes & Attachments", folderQName);
                            notesAttachments = folderInfo.getNodeRef();
                        }
                        existingDocument = nodeService.getChildByName(notesAttachments, ContentModel.ASSOC_CONTAINS,documentId);
                        if(existingDocument == null)
                        {
                            node = fileFolderService.create(notesAttachments,documentId,contentQName);
                            writer = fileFolderService.getWriter(node.getNodeRef());
                            writer.setMimetype("text/plain");
                            writer.putContent(noteItem2.getAttributes().get("Body").toString());
                        }
                    }
                }
            }
        }

        //The returned record object
        model.put("status", "Completed");
        log.debug("Completed");
        return model;
    }

    protected String filterName(String raw)
    {
        //decode String before working on it
        String decodedRaw = URLDecoder.decode(raw);

        // See regular expression in contentModel.xml:
        // (.*[\"\*\\\>\<\?\/\:\|]+.*)|(.*[\.]?.*[\.]+$)|(.*[ ]+$)
        String trimmed = decodedRaw.trim();
        StringBuilder result = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++)
        {
            char c = trimmed.charAt(i);
            //
            if ((c == '\"') || (c == '*') || (c == '\\') || (c == '>') || (c == '<') || (c == '?') || (c == '/') || (c == ':') || (c
                    == '|')
                    || ((i == trimmed.length() - 1) && (c == '.')))
            {
                result.append('_');
            }
            else
            {
                result.append(c);
            }
        }
        return result.toString();
    }
    private String getKeyPrefix(@NotNull String id)
    {
        return id.substring(0,3);
    }


    /**
     *  Get the filtered list of objects.  The filtered list provides only those objects that can work with the connector
     * @param connection
     * @return
     */
    private List<Map> getSObjectDescriptions(Connection<Salesforce> connection)
    {
        return canvasService.getFilteredObjects(canvasService.getObjects(connection));
    }


    /**
     * Get the object API name so that it be used in a SOQL query
     * @param keyPrefix
     * @param connection
     * @return
     */
    private String getObjectType(String keyPrefix, Connection<Salesforce> connection)
    {
        //Do we have a cached copy of the objects?
        if (sObjectDescriptions !=null && !sObjectDescriptions.isEmpty())
        {
            for (Map obj : sObjectDescriptions)
            {
                //Find a match
                if (obj.get(KEYPREFIX) != null)
                {
                    String objPrefix = obj.get(KEYPREFIX).toString();
                    if (keyPrefix.equals(objPrefix))
                    {
                        return obj.get(NAME).toString();
                    }
                }
            }
        }

        //No match was found or there is not a cached list of the objects. We know that the key is from a known object so our object
        // If there is cached list and there is no match we need to reload it to get any updates.
        sObjectDescriptions = getSObjectDescriptions(connection);
        return getObjectType(keyPrefix, connection);
    }
    public void setCanvasService(CanvasService canvasService)
    {
        this.canvasService = canvasService;
    }
    public void setNodeService(NodeService nodeService) { this.nodeService = nodeService;}
    public void setFileFolderService(FileFolderService fileFolderService) {this.fileFolderService = fileFolderService; }
    public void setSearchService(SearchService searchService){this.searchService = searchService; }
    public void setMimetypeService(MimetypeService mimetypeService){this.mimetypeService = mimetypeService; }
}
