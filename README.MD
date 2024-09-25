1. Go to the permanent tokens section https://jetbrains.team/m/me/authentication?tab=PermanentTokens
2. Choose either full access or all required permissions depending on your task <img width="745" alt="image" src="https://github.com/JetBrains/space-export-data/assets/1004115/4abb612a-1fbd-403f-b22f-d2b288cd9b2c">
3. Clone this repository locally and run the command.

# Export data

All export data is placed into `export` folder.

## Export chats

Example for exporting all chats you have access to:
```
./space-export --token=<token> --org=<organizationName>.jetbrains.space --format=json chats
```

By default json format is used, the path is `export/chats/channels/channel_name/history.json`, 
all chat attachments downloaded into the same respective channel directory.

## Export documents

Example for exporting all documents you have access to:
```
./space-export --token=<token> --org=<organizationName>.jetbrains.space documents
```

By default all personal and project documents will stored under the path `export/documents/`.

If you only want to export personal documents pass the `--scope=personal` command line argument.

If you want to export documents from specific project pass the `--scope=project --project-key=<projectKey>` command line argument.
