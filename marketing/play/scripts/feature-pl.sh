#!/bin/bash
# Polish feature graphic: reuses left half (icon + "FastMask") of the English one, new tagline + phone from raw-pl/01.png
S=/private/tmp/claude-501/-Users-pawelorzech-Programowanie-FastMask/6a7ca0e1-8528-4237-b2f6-06ffe8b6de22/scratchpad/store
cd $S; T=$S/tmp; mkdir -p new/pl-PL/featureGraphic
E=new/en-US/featureGraphic/01.png; AMB='#C76B00'
PW=${PW:-594}; PH=${PH:-700}; ANG=${ANG:--6.5}; PX=${PX:-555}; PY=${PY:-30}
# phone: top part of the screen, rounded, outlined, shadowed
magick \( raw-pl/01.png -crop 1080x265+0+0 +repage \) \( raw-pl/01.png -crop 1080x1150+0+312 +repage \) -append -resize ${PW}x -alpha off -crop ${PW}x${PH}+0+0 +repage $T/pf.png
magick -size ${PW}x${PH} xc:black -fill white -draw "roundrectangle 0,0,$((PW-1)),$((PH+60)),30,30" $T/pm.png
magick $T/pf.png $T/pm.png -alpha off -compose CopyOpacity -composite $T/pc.png
magick -size ${PW}x${PH} xc:none -stroke '#F0BE8A' -strokewidth 4 -fill none -draw "roundrectangle 3,3,$((PW-4)),$((PH+60)),30,30" $T/po.png
magick $T/pc.png $T/po.png -composite -background none -rotate $ANG +repage $T/pr.png
magick $T/pr.png \( +clone -background black -shadow 40x24+0+10 \) +swap -background none -layers merge +repage $T/prs.png
# left side + tagline
magick $E -crop 540x500+0+0 +repage -fill "$AMB" -draw "rectangle 0,320,540,430" \
  -font fonts/Regular.ttf -pointsize 38 -interline-spacing -2 -fill '#F5D1A5' -gravity NorthWest -annotate +66+330 "Maskowane adresy dla
Twojego konta Fastmail." $T/left.png
magick -size 1024x500 xc:"$AMB" $T/prs.png -geometry +${PX}+${PY} -composite $T/left.png -geometry +0+0 -composite -alpha off -depth 8 PNG24:new/pl-PL/featureGraphic/01.png
magick identify -format '%f %w %h %[channels]\n' new/pl-PL/featureGraphic/01.png
