#!/bin/bash
# usage: compose-tab.sh  (builds ten/seven inch cards for en-US and pl-PL)
S=/private/tmp/claude-501/-Users-pawelorzech-Programowanie-FastMask/6a7ca0e1-8528-4237-b2f6-06ffe8b6de22/scratchpad/store
cd $S; T=$S/tmp
en=("Every mask,
one tap to copy." "Turn a mask off the
moment spam starts." "A new address before
the sign-up form loads." "Your wallpaper,
your colors.")
pl=("Każda maska, kopiowana
jednym dotknięciem." "Wyłącz maskę, gdy
tylko zacznie się spam." "Nowy adres, zanim
załaduje się formularz." "Twoja tapeta,
twoje kolory.")
for size in ten seven; do
 if [ $size = ten ]; then W=2560; H=1600; raw=raw-t10; else W=1920; H=1200; raw=raw-t7; fi
 for loc in en pl; do
  [ $loc = en ] && L=en-US || L=pl-PL
  O=new/$L/${size}InchScreenshots; mkdir -p $O
  for i in 1 2 3 4; do
   n=0$i; [ $loc = en ] && cap="${en[$((i-1))]}" || cap="${pl[$((i-1))]}"
   DW=$(( W*88/100 )); DH=$(( DW*10/16 )); X=$(( (W-DW)/2 )); Y=$(( W*125/1000 ))
   R=$(( W*14/1000 )); PS=$(( W*45/1000 )); M=$(( W*40/1000 )); OW=$(( W*2/1000+1 ))
   magick -size ${DW}x${DH} xc:black -fill white -draw "roundrectangle 0,0,$((DW-1)),$((DH-1)),$R,$R" $T/tm.png
   magick $raw/$loc/$n.png -resize ${DW}x${DH}! -alpha off $T/tm.png -compose CopyOpacity -composite $T/ts.png
   magick $T/ts.png \( +clone -background black -shadow 35x$((W/60))+0+0 \) +swap -background none -layers merge +repage $T/tsh.png
   read SW SH < <(magick $T/tsh.png -format '%w %h' info:); off=$(( (SW-DW)/2 ))
   magick -size ${DW}x${DH} xc:none -stroke '#E9C9A6' -strokewidth $OW -fill none -draw "roundrectangle 1,1,$((DW-2)),$((DH-2)),$R,$R" $T/to.png
   magick -size ${W}x${H} xc:'#FFE6C8' $T/tsh.png -geometry +$((X-off))+$((Y-off+W/180)) -composite $T/ts.png -geometry +$X+$Y -composite $T/to.png -geometry +$X+$Y -composite \
    -stroke none -font fonts/SemiBold.ttf -pointsize $PS -interline-spacing -$((PS/6)) -fill '#753200' -gravity NorthWest -annotate +${M}+$((W*20/1000)) "$cap" \
    -alpha off -depth 8 PNG24:$O/$n.png
  done
  magick identify -format '%f %w %h %[channels]\n' $O/*.png
 done
done
