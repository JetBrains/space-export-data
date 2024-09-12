1. Go to the permanent tokens section https://jetbrains.team/m/me/authentication?tab=PermanentTokens
2. Choose either full access or all required permissions depending on your task <img width="745" alt="image" src="https://github.com/JetBrains/space-export-data/assets/1004115/4abb612a-1fbd-403f-b22f-d2b288cd9b2c">
3. Clone this repository locally and run the command. Example for exporting all chats you have access to:
```
./space-export --token=MY_TOKEN --org=ORGANIZATION_NAME.jetbrains.space --format=json chats
```

All export data is placed into `export` folder.

By default json format is used, the path is `export/channels/channel_name/history.json`, 
all chat attachments downloaded into the same respective channel directory.
