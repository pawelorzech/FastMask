#!/bin/bash
S=/private/tmp/claude-501/-Users-pawelorzech-Programowanie-FastMask/6a7ca0e1-8528-4237-b2f6-06ffe8b6de22/scratchpad/store
cd $S; O=new/pl-PL/phoneScreenshots; T=$S/tmp; mkdir -p $T $O
W=842; H=1895; X=119; Y=360
caps=("Każda maska, kopiowana
jednym dotknięciem." "Wyłącz maskę, gdy
tylko zacznie się spam." "Nowy adres, zanim
załaduje się formularz." "Losowa maska: świeży
adres jednym dotknięciem." "Twoja tapeta,
twoje kolory." "Przyjemny dla oczu
w nocy.")
for i in 1 2 3 4 5 6; do
 W=842; H=1895; X=119; Y=360; PS=84; CROP=2424
 [ $i = 4 ] && { W=744; H=1584; X=168; Y=250; CROP=2300; }
 [ $i = 1 ] && PS=76
 [ $i = 4 ] && PS=72
 n=0$i; bg='#FFE6C8'; fg='#753200'; ol='#E9C9A6'
 [ $i = 6 ] && { bg='#2B1A0E'; fg='#FFE6C8'; ol='#5A3A20'; }
 magick -size ${W}x${H} xc:black -fill white -draw "roundrectangle 0,0,$((W-1)),$((H-1)),48,48" $T/mask.png
 magick raw-pl/$n.png -crop 1080x${CROP}+0+0 +repage -resize ${W}x${H}! -alpha off $T/mask.png -compose CopyOpacity -composite $T/shot.png
 magick $T/shot.png \( +clone -background black -shadow 35x30+0+0 \) +swap -background none -layers merge +repage $T/sh.png
 magick $T/sh.png -format '%w %h' info: > $T/dim; read SW SH < $T/dim
 off=$(( (SW-W)/2 ))
 magick -size ${W}x${H} xc:none -stroke "$ol" -strokewidth 4 -fill none -draw "roundrectangle 2,2,$((W-3)),$((H-3)),48,48" $T/ol.png
 magick -size 1080x1920 xc:"$bg" $T/sh.png -geometry +$((X-off))+$((Y-off+14)) -composite $T/shot.png -geometry +$X+$Y -composite $T/ol.png -geometry +$X+$Y -composite \
   -stroke none -font fonts/SemiBold.ttf -pointsize $PS -interline-spacing -16 -fill "$fg" -gravity NorthWest -annotate +72+60 "${caps[$((i-1))]}" \
   -alpha off -depth 8 PNG24:$O/$n.png
done
magick identify -format '%f %w %h %[channels] %b\n' $O/*.png
mkdir -p new/pl-PL/icon; cp new/en-US/icon/01.png new/pl-PL/icon/01.png
./feature-pl.sh
