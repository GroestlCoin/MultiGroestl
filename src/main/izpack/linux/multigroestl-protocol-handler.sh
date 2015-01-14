gconftool-2 -t string -s /desktop/gnome/url-handlers/groestlcoin/command "java -splash:doesnotexist.png -jar $INSTALL_PATH/multigroestl-exe.jar %s"
gconftool-2 -s /desktop/gnome/url-handlers/groestlcoin/needs_terminal false -t bool
gconftool-2 -t bool -s /desktop/gnome/url-handlers/groestlcoin/enabled true