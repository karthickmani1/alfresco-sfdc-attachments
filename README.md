<b>Get Notes & Attachments from Salesforce</b>

This project is to retrieve all the Notes & Attachments from Salesforce Account/Opportunity objects and export the files to a pre-defined folder structure in an Alfresco instance.

The webscripts are used to retrieve Notes and Attachments from individual Account/Opportunity and also from all the Accounts/Opportunuties in Salesforce. 

<b>Webscripts</b>

SFDCGetAllAccountNA - is used to retrieve all the Accounts object's Notes & Attachments from Salesforce

SFDCGetAllOppsNA - is used to retrieve all the Opportunuties object's Notes & Attachments from Salesforce

SFDCWebscript - is used to retrieve all the Notes & Attachments of an Account/Opportunity (based on the argument specified when executing the webscript)

<b>Scheduled Jobs</b>

SFDCAccountJobExecuter - the scheduled job is run to retrieve all the Accounts object's Notes & Attachments from Salesforce

SFDCOpportunityJobExecuter - the scheduled job is run to retrieve all the Opportunities object's Notes & Attachments from Salesforce
# alfresco-salesforce-attachments
# aps-salesforce-attachments
