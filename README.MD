# Export data from JetBrains Space

This solution allows you to export Chats and Documents from your Space organization and save them locally in the `.json` format. 
Note that you can only export data that your Space account permissions allow you to access.

## Step 1. Get a Personal Token from Space
1. Log into your Space organization.
2. In top-left corner click your avatar, then navigate to **Preferences** > **Personal Tokens**.
3. Click **New personal token**.
4. To be able to export all data you have access to, select **Access** > **Full**. Otherwise, choose the limited scope of permissions to filter out unwanted data.
5. Create and save your token.

[See the detailed instructions on creating a personal token in Space](https://www.jetbrains.com/help/space/personal-tokens.html#create-a-personal-token)

## Step 2. Clone this entire repository locally

## Step 3. Run the script
* The export of Chats and Documents is performed separately by executing the respective commands and passing specific arguments.
* All exported data will be placed into the `export/` folder.

### Export Chats

To export all chats you have access to, run the following command from the `./space-export` directory:
```
./space-export --token=<your-personal-token> --org=<your-organization>.jetbrains.space --format=json chats 
```
* Please note that the `chats` command should be placed at the end of the command line following all arguments.
* All exported chat messages will be stored in: `export/chats/channels/<channel-name>/history.json` 
* Chat attachments will be downloaded into the same respective channel directory.

### Export Documents

To export all documents you have access to, run the following command from the `./space-export` directory:
```
./space-export --token=<your-personal-token> --org=<your-organization>.jetbrains.space documents 
```
* Please note that the `documents` command should be placed at the end of the command line following all arguments.
* If you only want to export personal documents, add the argument `--scope=personal` to the command line.
* To export documents from specific project, add the argument `--scope=project --project-key=<projectKey>` to the command line. To find out the project key, go to the project page in Space.
* The default directory for all exported personal and project documents is `export/documents/`.
