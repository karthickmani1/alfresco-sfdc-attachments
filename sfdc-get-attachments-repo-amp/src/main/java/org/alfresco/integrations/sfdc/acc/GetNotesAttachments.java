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
import org.alfresco.service.cmr.site.SiteService;
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
 * Web Script to get Notes and attachments from Salesforce
 *
 *  *
 * The core service {@link CanvasService CanvasSerivce} is loaded in a subsystem. A proxy is
 * used to make sure that it is available when the behaviour is used.
 *
 * url /sfdc/extension/objects/{object}/{id}?fields={fields}
 *
 * object: The api name of the Salesforce object (ex. Account or Node__c)
 * id: The id of the Salesforce Record (ex. 001B000000LqvYtIAJ)
 * fields: A common separated list of the object fields to be returned.  If fields is not passed then all fields will be returned
 *
 */
public class GetNotesAttachments extends DeclarativeWebScript
{
    private final static Log log = LogFactory.getLog(GetNotesAttachments.class);

    public static final String PARAM_OBJECT = "object";
    public static final String PARAM_ID = "id";
    public static final String PARAM_FIELDS = "fields";
    public static final String MODEL_ROW = "row";

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
        //NodeRef destLib = siteService.getContainer("salesforce-connector","documentLibrary");
        //NodeRef destRoot = nodeService.getChildByName(destLib, ContentModel.ASSOC_CONTAINS,"Account");
        NodeRef destFolder = null;
        NodeRef notesAttachments;
        NodeRef existingDocument;
        Map<String, Object> model = new HashMap<>();

        Map<String, String> templateArgs = req.getServiceMatch().getTemplateVars();
        String o = templateArgs.get(PARAM_OBJECT);
        String i = templateArgs.get(PARAM_ID);
        String fields = req.getParameter(PARAM_FIELDS);


        log.debug("#### Object: " + o + ", Id: " + i + ", fields: " + fields);

        model.put(PARAM_OBJECT, o);
        model.put(PARAM_ID, i);
        model.put(PARAM_FIELDS, fields);

        String queryStr = "Select Name, Id, ParentId from Attachment where ParentId = " + "'" + i + "'";
        String qryNotes = "Select Title, Id, ParentId, Body from Note where ParentId = " + "'" + i + "'";
        QueryResult qryResult = connection.getApi().queryOperations().query(queryStr);
        QueryResult qryNotesResult = connection.getApi().queryOperations().query(qryNotes);
        List<ResultItem> items = qryResult.getRecords();
        List<ResultItem> noteItems = qryNotesResult.getRecords();
        String documentId = "";
        FileInfo folderInfo;
        FileInfo node;
        InputStream apiResult;
        ContentWriter writer;
        String qryOpp = "";
        String qryAccount = "";
        String parentId = "";
        String prefix = "";
        String name = "";
        String title = "";

        for (ResultItem item : items)
        {
            documentId = item.getAttributes().get("Id").toString();
            parentId = item.getAttributes().get("ParentId").toString();
            prefix = getKeyPrefix(parentId);
            name = getObjectType(prefix, connection);
            if(name.equals("Account"))
            {
                qryAccount = "@sobjectAccount\\:Id:\"" + parentId +"\"";
                ResultSet resAcc = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, qryAccount);
                log.info(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
                log.info(qryOpp);
                if (resAcc != null && resAcc.length() > 0) {
                    for (int j = 0; j < resAcc.length(); j++) {
                        destFolder = resAcc.getNodeRef(j);
                    }
                }
            }
            else if(name.equals("Opportunity"))
            {
                qryOpp = "@sobjectOpportunity\\:Id:\"" + parentId +"\"";
                ResultSet results = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, qryOpp);
                log.info(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
                log.info(qryOpp);
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
            existingDocument = nodeService.getChildByName(notesAttachments, ContentModel.ASSOC_CONTAINS,item.getAttributes().get("Name").toString());
            if(existingDocument == null)
            {
                node = fileFolderService.create(notesAttachments,item.getAttributes().get("Name").toString(),contentQName);
                apiResult = connection.getApi().sObjectsOperations().getBlob("Attachment", documentId, "Body");
                writer = fileFolderService.getWriter(node.getNodeRef());
                writer.setMimetype(mimetypeService.guessMimetype(node.getName()));
                writer.putContent(apiResult);
            }
        }

        for (ResultItem noteItem : noteItems)
        {
            documentId = noteItem.getAttributes().get("Title").toString();
            /*documentId = documentId.replaceAll("\\/","");
            documentId = documentId.replaceAll("|","_");
            documentId = documentId.replaceAll("\"","_");
            documentId = documentId.replaceAll("\\*","");
            documentId = documentId.replaceAll("\\\\","_");
            documentId = documentId.replaceAll("\\?","_");
            documentId = documentId.replaceAll("<","_");
            documentId = documentId.replaceAll(">","_");
            documentId = documentId.replaceAll(":","_");*/
            documentId = filterName(documentId);
            parentId = noteItem.getAttributes().get("ParentId").toString();
            prefix = getKeyPrefix(parentId);
            name = getObjectType(prefix, connection);
            if(name.equals("Account"))
            {
                qryAccount = "@sobjectAccount\\:Id:\"" + parentId +"\"";
                ResultSet resAcc = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, qryAccount);
                log.info(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
                log.info(qryOpp);
                if (resAcc != null && resAcc.length() > 0) {
                    for (int j = 0; j < resAcc.length(); j++) {
                        destFolder = resAcc.getNodeRef(j);
                    }
                }
            }
            else if(name.equals("Opportunity"))
            {
                qryOpp = "@sobjectOpportunity\\:Id:\"" + parentId +"\"";
                ResultSet results = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, qryOpp);
                log.info(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
                log.info(qryOpp);
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
                //apiResult = connection.getApi().sObjectsOperations().getBlob("Note", documentId, "Body");
                writer = fileFolderService.getWriter(node.getNodeRef());
                writer.setMimetype("text/plain");
                writer.putContent(noteItem.getAttributes().get("Body").toString());
            }
        }

        //The returned record object
        model.put(MODEL_ROW, "Completed");

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
