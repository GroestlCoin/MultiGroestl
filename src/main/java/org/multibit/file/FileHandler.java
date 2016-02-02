/**
 * Copyright 2012 multibit.org
 *
 * Licensed under the MIT license (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.multibit.file;

import com.google.groestlcoin.core.BlockChain;
import com.google.groestlcoin.core.ECKey;
import com.google.groestlcoin.core.Utils;
import com.google.groestlcoin.core.Wallet;
import com.google.groestlcoin.crypto.KeyCrypterException;
import org.multibit.ApplicationDataDirectoryLocator;
import org.multibit.controller.Controller;
import org.multibit.controller.bitcoin.BitcoinController;
import org.multibit.message.Message;
import org.multibit.message.MessageManager;
import org.multibit.model.bitcoin.BitcoinModel;
import org.multibit.model.bitcoin.WalletData;
import org.multibit.model.bitcoin.WalletInfoData;
import org.multibit.model.core.CoreModel;
import org.multibit.network.MultiBitService;
import org.multibit.store.MultiBitWalletProtobufSerializer;
import org.multibit.store.MultiBitWalletVersion;
import org.multibit.store.WalletVersionException;
import org.multibit.viewsystem.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Class consolidating the File IO in MultiBit for wallets and wallet infos.
 * 
 * @author jim
 * 
 */
public class FileHandler {
    private static Logger log = LoggerFactory.getLogger(FileHandler.class);

    public static final String USER_PROPERTIES_FILE_NAME = "multigroestl.properties";
    public static final String USER_PROPERTIES_HEADER_TEXT = "multigroestl";

    private final Controller controller;
    private final BitcoinController bitcoinController;
    
    private static final int MAX_FILE_SIZE = 1024 * 1024 * 1024; // Dont read files greater than 1 gigabyte.

    private MultiBitWalletProtobufSerializer walletProtobufSerializer;

    public FileHandler(BitcoinController bitcoinController) {
        this.bitcoinController = bitcoinController;
        this.controller = this.bitcoinController;

        walletProtobufSerializer = new MultiBitWalletProtobufSerializer();
    }

    /**
     * Load up a WalletData from a specified wallet file.
     * If the main wallet cannot be loaded, the most recent backup is tried, 
     * followed by the next recent.
     * 
     * @param walletFile the file of the wallet to load
     * @return WalletData - the walletData for the created wallet
     * @throws WalletLoadException
     * @throws WalletVersionException
     */
    public WalletData loadFromFile(File walletFile) throws WalletLoadException, WalletVersionException {
        if (walletFile == null) {
            return null;
        }

        String walletFilenameToUseInModel = walletFile.getAbsolutePath();

        try {
            // See if the wallet is serialized or protobuf.
            WalletInfoData walletInfo;

            if (isWalletSerialised(walletFile)) {
                // Serialised wallets are no longer supported.
                throw new WalletLoadException("Could not load wallet '" + walletFilenameToUseInModel
                        + "'. Serialized wallets are no longer supported.");
            } else {
                walletInfo = new WalletInfoData(walletFilenameToUseInModel, null, MultiBitWalletVersion.PROTOBUF_ENCRYPTED);
            }

            // If the wallet file is missing or empty but the backup file exists
            // load that instead. This indicates that the write was interrupted
            // (e.g. power loss).
            boolean useBackupWallets = ( !walletFile.exists() || walletFile.length() == 0 );
            boolean walletWasLoadedSuccessfully = false;
            Collection<String> errorMessages = new ArrayList<String>();

            Wallet wallet = null;

            // Try the main wallet first unless it is obviously broken.
            if (!useBackupWallets) {
                FileInputStream fileInputStream = new FileInputStream(walletFile);
                InputStream stream = null;

                try {
                    stream = new BufferedInputStream(fileInputStream);
                    wallet = Wallet.loadFromFileStream(stream);
                    walletWasLoadedSuccessfully = true;
                } catch (WalletVersionException wve) {
                    // We want this exception to propagate out.
                    throw wve;
                } catch (Exception e) {
                    e.printStackTrace();
                    String description = e.getClass().getCanonicalName() + " " + e.getMessage();
                    log.error(description);
                    errorMessages.add(description);
                } finally {
                    if (stream != null) {
                        stream.close();
                    }
                    fileInputStream.close();
                }
            }
            
             if (!walletWasLoadedSuccessfully) {
                 // If the main wallet was not loaded successfully, work out the best backup
                 // wallets to try and load them.
                 useBackupWallets = true;
                 
                 Collection<String> backupWalletsToTry = BackupManager.INSTANCE.calculateBestWalletBackups(walletFile, walletInfo);

                 Iterator<String> iterator = backupWalletsToTry.iterator();
                 while (!walletWasLoadedSuccessfully && iterator.hasNext()) {
                    String walletToTry = iterator.next();

                    FileInputStream fileInputStream = new FileInputStream(new File(walletToTry));
                    InputStream stream = null;

                    try {
                        stream = new BufferedInputStream(fileInputStream);
                        wallet = Wallet.loadFromFileStream(stream);
                        walletWasLoadedSuccessfully = true;
                        
                        // Mention to user that backup is being used.
                        // Report failure to user.
                        MessageManager.INSTANCE.addMessage(new Message(bitcoinController.getLocaliser().getString("fileHandler.walletCannotLoadUsingBackup",
                                new String[]{walletFilenameToUseInModel, walletToTry})));
                    } catch (Exception e) {
                        e.printStackTrace();
                        String description = e.getClass().getCanonicalName() + " " + e.getMessage();
                        log.error(description);
                        errorMessages.add(description);
                    } finally {
                        if (stream != null) {
                            stream.close();
                        }
                        fileInputStream.close();
                    }
                }
            }

            WalletData perWalletModelData = null;

            if (walletWasLoadedSuccessfully) {
                if (walletInfo != null) {
                    // If wallet description is only in the wallet, copy it to
                    // the wallet info
                    // (perhaps the user deleted/ did not copy the info file).
                    String walletDescriptionInInfo = walletInfo.getProperty(WalletInfoData.DESCRIPTION_PROPERTY);
                    if ((walletDescriptionInInfo == null || walletDescriptionInInfo.length() == 0)
                            && wallet.getDescription() != null) {
                        walletInfo.put(WalletInfoData.DESCRIPTION_PROPERTY, wallet.getDescription());
                    }
                    
                    // Check that only receiving addresses that appear in a key
                    // appear in the wallet info.
                    walletInfo.checkAllReceivingAddressesAppearInWallet(wallet);
                    
                    // Make sure the version type in the info file matches what was actually loaded.
                    // (A backup with a different encryption type might have been used).
                    walletInfo.setWalletVersion(wallet.getVersion());
                }

                // Ensure that the directories for the backups of the private
                // keys, rolling backups and regular backups exist.
                BackupManager.INSTANCE.createBackupDirectories(walletFile);

                // Add the new wallet into the model.
                wallet.setNetworkParameters(bitcoinController.getModel().getNetworkParameters());

                perWalletModelData = bitcoinController.getModel().addWallet(this.bitcoinController, wallet,
                        walletFilenameToUseInModel);

                perWalletModelData.setWalletInfo(walletInfo);

                // If the backup files were used save them immediately and don't
                // delete any rolling backups.
                if (useBackupWallets) {
                    // Wipe the wallet backup property so that the rolling
                    // backup file will not be overwritten
                    walletInfo.put(BitcoinModel.WALLET_BACKUP_FILE, "");

                    // Save the wallet immediately just to be on the safe side.
                    savePerWalletModelData(perWalletModelData, true);
                }

                synchronized (walletInfo) {
                    perWalletModelData.setDirty(false);
                }
            } else {
                // No wallet was loaded successfully.
                // Wipe the rolling backup property to ensure that file wont be deleted.
                if (walletInfo != null) {
                    walletInfo.put(BitcoinModel.WALLET_BACKUP_FILE, "");
                }
                
                // Report failure to user.
                String messageText = bitcoinController.getLocaliser().getString("fileHandler.unableToLoadWalletOrBackups", new String[] {walletFilenameToUseInModel});
                if (!errorMessages.isEmpty()) {
                    StringBuilder errorMessagesAsString = new StringBuilder();
                    for (String errorText : errorMessages) {
                        if (errorMessagesAsString.length()>0) {
                            errorMessagesAsString.append("\n");
                        }
                        errorMessagesAsString.append(errorText);
                    }
                    messageText = messageText + "\n" + bitcoinController.getLocaliser().getString("deleteWalletConfirmDialog.walletDeleteError2", new String[]{errorMessagesAsString.toString()});
                }
                MessageManager.INSTANCE.addMessage(new Message(messageText));
            }
            return perWalletModelData;
        } catch (WalletVersionException wve) {
            // We want this to propagate out.
            throw wve;
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getClass().getCanonicalName() + " " + e.getMessage());
            throw new WalletLoadException(e.getClass().getCanonicalName() + " " + e.getMessage(), e);
        }
    }

    private boolean isWalletSerialised(File walletFile) {
        boolean isWalletSerialised = false;
        InputStream stream = null;
        try {
            // Determine what kind of wallet stream this is: Java Serialization
            // or protobuf format.
            stream = new BufferedInputStream(new FileInputStream(walletFile));
            isWalletSerialised = stream.read() == 0xac && stream.read() == 0xed;
        } catch (IOException e) {
            log.error(e.getClass().getCanonicalName() + " " + e.getMessage());
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    log.error(e.getClass().getCanonicalName() + " " + e.getMessage());
                }
            }
        }
        return isWalletSerialised;
    }

    /**
     * Save the perWalletModelData to file.
     * 
     * @param perWalletModelData the wallet data
     * @param forceWrite
     *            force the write of the perWalletModelData
     */
    public void savePerWalletModelData(WalletData perWalletModelData, boolean forceWrite) {
        if (perWalletModelData == null || perWalletModelData.getWalletFilename() == null) {
            return;
        }

        WalletInfoData walletInfo = perWalletModelData.getWalletInfo();

        if (walletInfo != null) {
            synchronized (walletInfo) {
                // Save the perWalletModelData if it is dirty or if forceWrite is true.
                if (perWalletModelData.isDirty() || forceWrite) {
                    // Normal write of data.
                    String walletInfoFilename = WalletInfoData.createWalletInfoFilename(perWalletModelData.getWalletFilename());
                    saveWalletAndWalletInfo(perWalletModelData, perWalletModelData.getWalletFilename(), walletInfoFilename);

                    // The perWalletModelData is no longer dirty.
                    perWalletModelData.setDirty(false);
                }
            }
        }
    }

    /**
     * Simply save the wallet and wallet info files.
     * Used for backup writes.
     *
     * @param perWalletModelData the wallet data
     * @param walletFilename the wallet filename
     * @param walletInfoFilename the wallet info filename
     */
    public void saveWalletAndWalletInfoSimple(WalletData perWalletModelData, String walletFilename, String walletInfoFilename) {
        File walletFile = new File(walletFilename);
        WalletInfoData walletInfo = perWalletModelData.getWalletInfo();

        FileOutputStream fileOutputStream = null;

        // Save the wallet file
        try {
            if (perWalletModelData.getWallet() != null) {
                // Wallet description is currently stored in the wallet info
                // file but is now available on the wallet itself.
                // Store the description from the wallet info in the wallet - in
                // the future the wallet value will be primary
                // and wallet infos can be deprecated.
                if (walletInfo != null) {
                    String walletDescriptionInInfoFile = walletInfo.getProperty(WalletInfoData.DESCRIPTION_PROPERTY);
                    if (walletDescriptionInInfoFile != null) {
                        perWalletModelData.getWallet().setDescription(walletDescriptionInInfoFile);
                    }
                }

                log.debug("Saving wallet file '" + walletFile.getAbsolutePath() + "' ...");
                if (MultiBitWalletVersion.SERIALIZED == walletInfo.getWalletVersion()) {
                    throw new WalletSaveException("Cannot save wallet '" + walletFile.getAbsolutePath() + "'. Serialized wallets are no longer supported.");
               } else {
                    // See if there are any encrypted private keys - if there
                    // are the wallet will be saved
                    // as encrypted and the version set to PROTOBUF_ENCRYPTED.
                    boolean walletIsActuallyEncrypted = false;
                    Wallet wallet = perWalletModelData.getWallet();
                    // Check all the keys individually.
                    for (ECKey key : wallet.getKeychain()) {
                        if (key.isEncrypted()) {
                            walletIsActuallyEncrypted = true;
                            break;
                        }
                    }

                    if (walletIsActuallyEncrypted) {
                        walletInfo.setWalletVersion(MultiBitWalletVersion.PROTOBUF_ENCRYPTED);
                    }

                    if (MultiBitWalletVersion.PROTOBUF == walletInfo.getWalletVersion()) {
                        // Save as a Wallet message.
                        perWalletModelData.getWallet().saveToFile(walletFile);
                    } else if (MultiBitWalletVersion.PROTOBUF_ENCRYPTED == walletInfo.getWalletVersion()) {
                        fileOutputStream = new FileOutputStream(walletFile);

                        // Save as a Wallet message with a mandatory extension
                        // to prevent loading by older versions of multibit.
                        walletProtobufSerializer.writeWallet(perWalletModelData.getWallet(), fileOutputStream);
                    } else {
                        throw new WalletVersionException("Cannot save wallet '" + perWalletModelData.getWalletFilename()
                                + "'. Its wallet version is '" + walletInfo.getWalletVersion().toString()
                                + "' but this version of MultiBit does not understand that format.");
                    }
                }
                log.debug("... done saving wallet file.");
            }
        } catch (IOException ioe) {
            throw new WalletSaveException("Cannot save wallet '" + perWalletModelData.getWalletFilename(), ioe);
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (IOException e) {
                    throw new WalletSaveException("Cannot save wallet '" + perWalletModelData.getWalletFilename(), e);
                }
            }
        }

        // Write wallet info.
        walletInfo.writeToFile(walletInfoFilename, walletInfo.getWalletVersion());
    }

    /**
     * To protect the wallet data, the write is in steps: 1) Create a backup
     * file called <wallet file name>-<yyyymmddhhmmss>.wallet and copy the
     * original wallet to that 2) Write the new wallet to the walletFilename 3)
     * Delete the old backup file 4) Make the backup file in step 1) the new
     * backup file
     * 
     **/
    private void saveWalletAndWalletInfo(WalletData perWalletModelData, String walletFilename, String walletInfoFilename) {
        File walletFile = new File(walletFilename);
        WalletInfoData walletInfo = perWalletModelData.getWalletInfo();

        FileOutputStream fileOutputStream = null;

        // Save the wallet file
        try {
            if (perWalletModelData.getWallet() != null) {
                // Wallet description is currently stored in the wallet info
                // file but is now available on the wallet itself.
                // Store the description from the wallet info in the wallet - in
                // the future the wallet value will be primary
                // and wallet infos can be deprecated.
                if (walletInfo != null) {
                    String walletDescriptionInInfoFile = walletInfo.getProperty(WalletInfoData.DESCRIPTION_PROPERTY);
                    if (walletDescriptionInInfoFile != null) {
                        perWalletModelData.getWallet().setDescription(walletDescriptionInInfoFile);
                    }
                }
                String oldBackupFilename = perWalletModelData.getWalletInfo().getProperty(BitcoinModel.WALLET_BACKUP_FILE);
                File oldBackupFile = null;
                String newBackupFilename = null;
                if (null != oldBackupFilename && !"".equals(oldBackupFilename)) {
                    oldBackupFile = new File(oldBackupFilename);
                }
                if (walletInfo != null && (MultiBitWalletVersion.PROTOBUF == walletInfo.getWalletVersion()
                        || MultiBitWalletVersion.PROTOBUF_ENCRYPTED == walletInfo.getWalletVersion())) {
                    newBackupFilename = BackupManager.INSTANCE.createBackupFilename(walletFile, BackupManager.ROLLING_WALLET_BACKUP_DIRECTORY_NAME, false, false, BitcoinModel.WALLET_FILE_EXTENSION);
                    File newWalletBackupFile = new File(newBackupFilename);

                    // Rename the existing wallet to the newWalletBackupFile
                    boolean result = rename(walletFile, newWalletBackupFile);
                    log.debug("Result of the wallet rename to backup file was " + result);
                }

                log.debug("Saving wallet file '" + walletFile.getAbsolutePath() + "' ...");
                if (walletInfo != null && MultiBitWalletVersion.SERIALIZED == walletInfo.getWalletVersion()) {
                    throw new WalletSaveException("Cannot save wallet '" + walletFile.getAbsolutePath() + "'. Serialized wallets are no longer supported.");
                } else {
                    // See if there are any encrypted private keys - if there
                    // are the wallet will be saved
                    // as encrypted and the version set to PROTOBUF_ENCRYPTED.
                    boolean walletIsActuallyEncrypted = false;
                    Wallet wallet = perWalletModelData.getWallet();
                    // Check all the keys individually.
                    for (ECKey key : wallet.getKeychain()) {
                        if (key.isEncrypted()) {
                            walletIsActuallyEncrypted = true;
                            break;
                        }
                    }

                    if (walletIsActuallyEncrypted) {
                        if (walletInfo == null) {
                            walletInfo = new WalletInfoData(walletFilename, wallet, MultiBitWalletVersion.PROTOBUF_ENCRYPTED);
                            perWalletModelData.setWalletInfo(walletInfo);
                        } else {
                            walletInfo.setWalletVersion(MultiBitWalletVersion.PROTOBUF_ENCRYPTED);
                        }
                    }

                    if (walletInfo != null && MultiBitWalletVersion.PROTOBUF == walletInfo.getWalletVersion()) {
                        // Save as a Wallet message.
                        perWalletModelData.getWallet().saveToFile(walletFile);
                    } else if (walletInfo != null && MultiBitWalletVersion.PROTOBUF_ENCRYPTED == walletInfo.getWalletVersion()) {
                        // Create wallet output file if it does not exist
                        if (!walletFile.exists()) {
                            boolean createdOk = walletFile.createNewFile();
                            log.debug("Result of empty wallet create for file {} was {}", walletFile, createdOk);
                        } else {
                            log.debug("Wallet file {} already exists - does not need creating", walletFile);
                        }
                        fileOutputStream = new FileOutputStream(walletFile);

                        // Save as a Wallet message with a mandatory extension
                        // to prevent loading by older versions of multibit.
                        walletProtobufSerializer.writeWallet(perWalletModelData.getWallet(), fileOutputStream);
                    } else {
                        throw new WalletVersionException("Cannot save wallet '" + perWalletModelData.getWalletFilename()
                                + "'. Its wallet version is '" + (walletInfo == null ? "UNKNOWN" : walletInfo.getWalletVersion().toString())
                                + "' but this version of MultiBit does not understand that format.");
                    }
                }
                log.debug("... done saving wallet file. Wallet file size is " + walletFile.length() + " bytes.");

                if (MultiBitWalletVersion.PROTOBUF == walletInfo.getWalletVersion()
                        || MultiBitWalletVersion.PROTOBUF_ENCRYPTED == walletInfo.getWalletVersion()) {
                    if (newBackupFilename != null) {
                        perWalletModelData.getWalletInfo().put(BitcoinModel.WALLET_BACKUP_FILE, newBackupFilename);
                    }

                    // Delete the oldBackupFile unless the user has manually opened it.
                    boolean userHasOpenedBackupFile = false;
                    List<WalletData> perWalletModelDataList = this.bitcoinController.getModel().getPerWalletModelDataList();
                    if (perWalletModelDataList != null) {
                        for (WalletData perWalletModelDataLoop : perWalletModelDataList) {
                            if ((oldBackupFilename != null && oldBackupFilename.equals(perWalletModelDataLoop.getWalletFilename()))
                                    || (newBackupFilename != null && newBackupFilename.equals(perWalletModelDataLoop
                                            .getWalletFilename()))) {
                                userHasOpenedBackupFile = true;
                                break;
                            }
                        }
                    }
                    if (!userHasOpenedBackupFile) {
                        SecureFiles.secureDelete(oldBackupFile);
                    }
                }
            }
        } catch (IOException ioe) {
            String message = "Cannot save wallet '" + perWalletModelData.getWalletFilename();
            log.error(message + " (1) " + ioe.getClass().getCanonicalName() + " " + ioe.getMessage());
            throw new WalletSaveException(message, ioe);
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (IOException e) {
                    String message = "Cannot save wallet '" + perWalletModelData.getWalletFilename();
                    log.error(message + " (2) " + e.getClass().getCanonicalName() + " " + e.getMessage());
                    throw new WalletSaveException(message, e);
                }
            }
        }

        // Write wallet info.
        walletInfo.writeToFile(walletInfoFilename, walletInfo.getWalletVersion());
    }


    /**
     * Backup the private keys of the active wallet to a file with name <wallet-name>-data/key-backup/<wallet
     * name>-yyyymmddhhmmss.key
     *
     * @param passwordToUse The password to use
     * @return File to which keys were backed up, or null if they were not.
     * @throws KeyCrypterException
     */
    public File backupPrivateKeys(CharSequence passwordToUse) throws IOException, KeyCrypterException {
        File privateKeysBackupFile = null;

        // Only encrypted files are backed up, and they must have a non blank password.
        if (passwordToUse != null && passwordToUse.length() > 0) {
            if (controller.getModel() != null
                    && this.bitcoinController.getModel().getActiveWalletWalletInfo() != null
                    && this.bitcoinController.getModel().getActiveWalletWalletInfo().getWalletVersion() == MultiBitWalletVersion.PROTOBUF_ENCRYPTED) {
                // Save a backup copy of the private keys, encrypted with the passwordToUse.
                PrivateKeysHandler privateKeysHandler = new PrivateKeysHandler(this.bitcoinController.getModel().getNetworkParameters());
                String privateKeysBackupFilename = BackupManager.INSTANCE.createBackupFilename(new File(this.bitcoinController.getModel()
                        .getActiveWalletFilename()), BackupManager.PRIVATE_KEY_BACKUP_DIRECTORY_NAME, false, false, BitcoinModel.PRIVATE_KEY_FILE_EXTENSION);
                privateKeysBackupFile = new File(privateKeysBackupFilename);
                BlockChain blockChain = null;
                if (this.bitcoinController.getMultiBitService() != null) {
                    blockChain = this.bitcoinController.getMultiBitService().getChain();
                }

                privateKeysHandler.exportPrivateKeys(privateKeysBackupFile, this.bitcoinController.getModel().getActiveWallet(),
                        blockChain, true, passwordToUse, passwordToUse);
            } else {
                log.debug("Wallet '" + this.bitcoinController.getModel().getActiveWalletFilename()
                        + "' private keys not backed up as not PROTOBUF_ENCRYPTED");
            }
        } else {
            log.debug("Wallet '" + this.bitcoinController.getModel().getActiveWalletFilename()
                    + "' private keys not backed up password was blank or of zero length");
        }
        return privateKeysBackupFile;
    }

    /**
     * Copy an existing wallet to a backup file and delete the original.
     * Used in rolling backups
     * 
     * @param walletFile the wallet file
     * @return the new wallet backup name
     * @throws IOException
     */
    private String copyExistingWalletToBackupAndDeleteOriginal(File walletFile, boolean secureDelete) throws IOException {
        String newWalletBackupFilename = BackupManager.INSTANCE.createBackupFilename(walletFile, BackupManager.ROLLING_WALLET_BACKUP_DIRECTORY_NAME, false, false, BitcoinModel.WALLET_FILE_EXTENSION);
        File newWalletBackupFile = new File(newWalletBackupFilename);
        if (walletFile != null && walletFile.exists()) {
            FileHandler.copyFile(walletFile, newWalletBackupFile);
            if (walletFile.length() != newWalletBackupFile.length()) {
                throw new IOException("Failed to copy the existing wallet from '" + walletFile.getAbsolutePath() + "' to '"
                        + newWalletBackupFilename + "'");
            }

            if (!walletFile.getAbsolutePath().equals(newWalletBackupFile.getAbsolutePath())) {
                if (secureDelete) {
                    log.debug("Using secure delete to delete wallet file {}", walletFile);
                    SecureFiles.secureDelete(walletFile);
                } else {
                    log.debug("Result of wallet delete was {}", walletFile.delete());
                }
            }
        }

        return newWalletBackupFilename;
    }

    /**
        Rename a file from the original to the new File
     **/
    private boolean rename(File originalFile, File newFile) {
        try {
            if (Utils.isWindows()) {
                // Work around an issue on Windows whereby you can't rename over existing files.
                File canonical = newFile.getCanonicalFile();
                if (canonical.exists() && !canonical.delete())
                    throw new IOException("Failed to delete canonical destination file");
                if (originalFile.renameTo(canonical))
                    return true;  // else fall through.
                throw new IOException("Failed to rename " + originalFile + " to " + canonical);
            } else if (!originalFile.renameTo(newFile)) {
                throw new IOException("Failed to rename " + originalFile + " to " + newFile);
            }
            return true;
        } catch (IOException ioe) {
            log.error("Error in renaming file " + originalFile + "  to " + newFile, ioe);
            return false;
        }
     }

    /**
     * Secure delete the wallet and the wallet info file.
     * 
     * @param perWalletModelData the wallet data
     */
    public void deleteWalletAndWalletInfo(WalletData perWalletModelData) {
        if (perWalletModelData == null) {
            return;
        }

        File walletFile = new File(perWalletModelData.getWalletFilename());
        WalletInfoData walletInfo = perWalletModelData.getWalletInfo();
        String walletInfoFilenameAsString = WalletInfoData.createWalletInfoFilename(perWalletModelData.getWalletFilename());
        File walletInfoFile = new File(walletInfoFilenameAsString);

        synchronized (walletInfo) {
            // See if either of the files are readonly - abort.
            if (!walletFile.canWrite() || !walletInfoFile.canWrite()) {
                throw new DeleteWalletException(controller.getLocaliser().getString("deleteWalletException.walletWasReadonly"));
            }

            // Delete the wallet info file first, then the wallet.
            try {
                SecureFiles.secureDelete(walletInfoFile);
                SecureFiles.secureDelete(walletFile);
                walletInfo.setDeleted(true);
            } catch (IOException ioe) {
                log.error(ioe.getClass().getCanonicalName() + " " + ioe.getMessage());
                throw new DeleteWalletException(controller.getLocaliser().getString("deleteWalletException.genericCouldNotDelete",
                        new String[] { perWalletModelData.getWalletFilename() }));
            }
        }

        // If the wallet was deleted, delete the model data.
        if (walletInfo.isDeleted()) {
            this.bitcoinController.getModel().remove(perWalletModelData);
        }
    }

    public static void writeUserPreferences(BitcoinController bitcoinController) {
        final Controller controller = bitcoinController;
        // Save all the wallets' filenames in the user preferences.
        if (bitcoinController.getModel().getPerWalletModelDataList() != null) {
            List<WalletData> perWalletModelDataList = bitcoinController.getModel().getPerWalletModelDataList();

            List<String> orderList = new ArrayList<String>();
            List<String> earlyList = new ArrayList<String>();
            List<String> protobuf3List = new ArrayList<String>();

            for (WalletData perWalletModelData : perWalletModelDataList) {
                // Check if this is the initial empty WalletData
                if ("".equals(perWalletModelData.getWalletFilename()) || perWalletModelData.getWalletFilename() == null
                        || perWalletModelData.getWalletInfo() == null) {
                    continue;
                }

                // Do not save deleted wallets
                if (perWalletModelData.getWalletInfo().isDeleted()) {
                    log.debug("Not writing out info about wallet '" + perWalletModelData.getWalletFilename()
                            + "' as it has been deleted");
                    continue;
                }

                if (!orderList.contains(perWalletModelData.getWalletFilename())) {
                    orderList.add(perWalletModelData.getWalletFilename());
                }

                if (perWalletModelData.getWalletInfo().getWalletVersion() == MultiBitWalletVersion.PROTOBUF_ENCRYPTED) {
                    if (!protobuf3List.contains(perWalletModelData.getWalletFilename())) {
                        protobuf3List.add(perWalletModelData.getWalletFilename());
                    }
                } else if (perWalletModelData.getWalletInfo().getWalletVersion() == null
                        || perWalletModelData.getWalletInfo().getWalletVersion() == MultiBitWalletVersion.SERIALIZED
                        || perWalletModelData.getWalletInfo().getWalletVersion() == MultiBitWalletVersion.PROTOBUF) {
                    if (!earlyList.contains(perWalletModelData.getWalletFilename())) {
                        earlyList.add(perWalletModelData.getWalletFilename());
                    }
                }
            }

            int orderCount = 1;
            for (String walletFilename : orderList) {
                controller.getModel().setUserPreference(BitcoinModel.WALLET_ORDER_PREFIX + orderCount, walletFilename);
                orderCount++;
            }
            controller.getModel().setUserPreference(BitcoinModel.WALLET_ORDER_TOTAL, "" + orderList.size());

            int earlyCount = 1;
            for (String walletFilename : earlyList) {
                controller.getModel().setUserPreference(BitcoinModel.EARLY_WALLET_FILENAME_PREFIX + earlyCount, walletFilename);
                earlyCount++;
            }
            controller.getModel().setUserPreference(BitcoinModel.NUMBER_OF_EARLY_WALLETS, "" + earlyList.size());

            int protobuf3Count = 1;
            for (String walletFilename : protobuf3List) {
                controller.getModel().setUserPreference(BitcoinModel.PROTOBUF3_WALLET_FILENAME_PREFIX + protobuf3Count,
                        walletFilename);
                protobuf3Count++;
            }
            controller.getModel().setUserPreference(BitcoinModel.NUMBER_OF_PROTOBUF3_WALLETS, "" + protobuf3List.size());
            controller.getModel().setUserPreference(BitcoinModel.ACTIVE_WALLET_FILENAME,
                    bitcoinController.getModel().getActiveWalletFilename());
        }

        Properties userPreferences = controller.getModel().getAllUserPreferences();

        // If the view is marked as also requiring a backwards compatible
        // numeric field write that.
        @SuppressWarnings("deprecation")
        int currentViewNumericFormat = View.toOldViewNumeric(controller.getCurrentView());
        if (currentViewNumericFormat != 0) {
            userPreferences.put(CoreModel.SELECTED_VIEW, "" + currentViewNumericFormat);
        } else {
            // Make sure the old numeric value for a view is not in the user
            // properties.
            userPreferences.remove(CoreModel.SELECTED_VIEW);
        }
        // Write the user preference properties.
        OutputStream outputStream = null;
        try {
            String userPropertiesFilename;
            if ("".equals(controller.getApplicationDataDirectoryLocator().getApplicationDataDirectory())) {
                userPropertiesFilename = USER_PROPERTIES_FILE_NAME;
            } else {
                userPropertiesFilename = controller.getApplicationDataDirectoryLocator().getApplicationDataDirectory()
                        + File.separator + USER_PROPERTIES_FILE_NAME;
            }

            outputStream = new FileOutputStream(userPropertiesFilename);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(bufferedOutputStream, "UTF8");
            userPreferences.store(outputStreamWriter, USER_PROPERTIES_HEADER_TEXT);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    log.error(e.getClass().getCanonicalName() + " " + e.getMessage());
                } finally {
                    outputStream = null;
                }
            }
        }
    }

    public static Properties loadUserPreferences(ApplicationDataDirectoryLocator applicationDataDirectoryLocator) {
        Properties userPreferences = new Properties();
        try {
            String userPropertiesFilename;
            if ("".equals(applicationDataDirectoryLocator.getApplicationDataDirectory())) {
                userPropertiesFilename = USER_PROPERTIES_FILE_NAME;
            } else {
                userPropertiesFilename = applicationDataDirectoryLocator.getApplicationDataDirectory() + File.separator
                        + USER_PROPERTIES_FILE_NAME;
            }
            InputStream inputStream = new FileInputStream(userPropertiesFilename);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF8");
            userPreferences.load(inputStreamReader);
        } catch (IOException e) {
            // Ok - may not have been created yet.
        }

        return userPreferences;
    }

    /**
     * To support multiple users on the same machine, the checkpoints file is
     * installed into the program installation directory and is then copied to
     * the user's application data directory when MultiBit is first used.
     * 
     * Thus each user has their own copy of the blockchain.
     */
    public void copyCheckpointsFromInstallationDirectory(String destinationCheckpointsFilename) throws IOException {
        if (destinationCheckpointsFilename == null) {
            return;
        }

        // See if the block chain in the user's application data directory
        // exists.
        File destinationCheckpoints = new File(destinationCheckpointsFilename);

        if (!destinationCheckpoints.exists()) {
            // Work out the source checkpoints (put into the program
            // installation directory by the installer).
            File directory = new File(".");
            String currentWorkingDirectory = directory.getCanonicalPath();

            String filePrefix = MultiBitService.getFilePrefix();
            String checkpointsFilename = filePrefix + MultiBitService.CHECKPOINTS_SUFFIX;
            String sourceCheckpointsFilename = currentWorkingDirectory + File.separator + checkpointsFilename;
            File sourceBlockcheckpoints = new File(sourceCheckpointsFilename);
            if (sourceBlockcheckpoints.exists() && !destinationCheckpointsFilename.equals(sourceCheckpointsFilename)) {
                // It should exist since installer puts them in.
                log.info("Copying checkpoints from '" + sourceCheckpointsFilename + "' to '" + destinationCheckpointsFilename + "'");
                copyFile(sourceBlockcheckpoints, destinationCheckpoints);

                // Check all the data was copied.
                long sourceLength = sourceBlockcheckpoints.length();
                long destinationLength = destinationCheckpoints.length();
                if (sourceLength != destinationLength) {
                    String errorText = "Checkpoints were not copied to user's application data directory correctly.\nThe source checkpoints '"
                            + sourceCheckpointsFilename
                            + "' is of length "
                            + sourceLength
                            + "\nbut the destination checkpoints '"
                            + destinationCheckpointsFilename + "' is of length " + destinationLength;
                    log.error(errorText);
                    throw new FileHandlerException(errorText);
                }
            }
        }
    }

    public static void copyFile(File sourceFile, File destinationFile) throws IOException {
        if (!destinationFile.exists()) {
            destinationFile.createNewFile();
        }
        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;
        FileChannel source = null;
        FileChannel destination = null;
        try {
            fileInputStream = new FileInputStream(sourceFile);
            source = fileInputStream.getChannel();
            fileOutputStream = new FileOutputStream(destinationFile);
            destination = fileOutputStream.getChannel();
            long transfered = 0;
            long bytes = source.size();
            while (transfered < bytes) {
                transfered += destination.transferFrom(source, 0, source.size());
                destination.position(transfered);
            }
        } finally {
            if (source != null) {
                source.close();
                source = null;
            } else if (fileInputStream != null) {
                fileInputStream.close();
                fileInputStream = null;
            }
            if (destination != null) {
                destination.close();
                destination = null;
            } else if (fileOutputStream != null) {
                fileOutputStream.flush();
                fileOutputStream.close();
            }
        }
    }

    public static void writeFile(byte[] sourceBytes, File destinationFile) throws IOException {
        if (!destinationFile.exists()) {
            destinationFile.createNewFile();
        }
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(destinationFile);

            fileOutputStream.write(sourceBytes);
        } finally {
            if (fileOutputStream != null) {
                fileOutputStream.flush();
                fileOutputStream.close();
            }
        }
    }

    public static File createTempDirectory(String filePrefix) throws IOException {
        final File temp;

        temp = File.createTempFile(filePrefix, Long.toString(System.currentTimeMillis()));

        if (!(temp.delete())) {
            throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
        }

        if (!(temp.mkdir())) {
            throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
        }

        temp.deleteOnExit();

        return temp;
    }

    public static byte[] read(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File must be provided");
        }
        
        if ( file.length() > MAX_FILE_SIZE ) {
            throw new IOException("File '" + file.getAbsolutePath() + "' is too large to input");
        }

        byte []buffer = new byte[(int) file.length()];
        InputStream ios = null;
        try {
            ios = new FileInputStream(file);
            if ( ios.read(buffer) == -1 ) {
                throw new IOException("EOF reached while trying to read the whole file");
            }        
        } finally { 
            try {
                 if ( ios != null )  {
                      ios.close();
                 }
            } catch ( IOException e) {
                log.error(e.getClass().getName() + " " + e.getMessage());
            }
        }

        return buffer;
    }
}