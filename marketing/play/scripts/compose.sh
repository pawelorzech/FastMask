#!/bin/bash
S=/private/tmp/claude-501/-Users-pawelorzech-Programowanie-FastMask/6a7ca0e1-8528-4237-b2f6-06ffe8b6de22/scratchpad/store
cd $S; O=new/en-US/phoneScreenshots; T=$S/tmp; mkdir -p $T
W=842; H=1895; X=119; Y=360
caps=("Every mask,
one tap to copy." "Turn a mask off the
moment spam starts." "A new address before
the sign-up form loads." "Random mask: a fresh
address in one tap." "Your wallpaper,
your colors." "Easy on the eyes
at night.")
for i in 1 2 3 4 5 6; do
 n=0$i; bg='#FFE6C8'; fg='#753200'; ol='#E9C9A6'
 [ $i = 6 ] && { bg='#2B1A0E'; fg='#FFE6C8'; ol='#5A3A20'; }
 magick -size ${W}x${H} xc:black -fill white -draw "roundrectangle 0,0,$((W-1)),$((H-1)),48,48" $T/mask.png
 magick raw/$n.png -resize ${W}x${H}! -alpha off $T/mask.png -compose CopyOpacity -composite $T/shot.png
 magick $T/shot.png \( +clone -background black -shadow 35x30+0+0 \) +swap -background none -layers merge +repage $T/sh.png
 magick $T/sh.png -format '%w %h' info: > $T/dim; read SW SH < $T/dim
 off=$(( (SW-W)/2 ))
 magick -size ${W}x${H} xc:none -stroke "$ol" -strokewidth 4 -fill none -draw "roundrectangle 2,2,$((W-3)),$((H-3)),48,48" $T/ol.png
 magick -size 1080x1920 xc:"$bg" $T/sh.png -geometry +$((X-off))+$((Y-off+14)) -composite $T/shot.png -geometry +$X+$Y -composite $T/ol.png -geometry +$X+$Y -composite \
   -stroke none -font fonts/SemiBold.ttf -pointsize 84 -interline-spacing -16 -fill "$fg" -gravity NorthWest -annotate +72+60 "${caps[$((i-1))]}" \
   -alpha off -depth 8 PNG24:$O/$n.png
done
magick identify -format '%f %w %h %[channels] %b\n' $O/*.png
