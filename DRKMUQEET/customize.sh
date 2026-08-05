#!/system/bin/sh
ui_print '*******************************'
ui_print ' DRKMUQEET • Tuning Console'
ui_print ' Redmi Note 8 Pro / begonia'
ui_print '*******************************'
[ "$(getprop ro.product.device)" = begonia ] || ui_print '! Warning: tuned for begonia; installing in safe compatibility mode.'
ui_print '- Installed. Use Magisk Action or http://127.0.0.1:8080 after boot.'
