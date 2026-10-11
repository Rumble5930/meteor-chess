"""Generates the 12 piece sprites (assets/chess/pieces/{w,b}{p,n,b,r,q,k}.png) from DejaVu Sans chess glyphs."""
from PIL import Image, ImageDraw, ImageFont
import os

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/chess/pieces")
SIZE = 256
# Solid ("black") glyphs for every piece; colour comes from fill/outline.
GLYPHS = {"k": "\u265A", "q": "\u265B", "r": "\u265C", "b": "\u265D", "n": "\u265E", "p": "\u265F"}
STYLE = {
    "w": dict(fill=(247, 247, 247, 255), stroke=(18, 18, 18, 255)),
    "b": dict(fill=(34, 34, 38, 255), stroke=(235, 235, 235, 255)),
}

os.makedirs(OUT, exist_ok=True)
font = ImageFont.truetype(FONT, 215)
for color, st in STYLE.items():
    for piece, glyph in GLYPHS.items():
        img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        l, t, r, b = d.textbbox((0, 0), glyph, font=font, stroke_width=7)
        x = (SIZE - (r - l)) / 2 - l
        y = (SIZE - (b - t)) / 2 - t
        d.text((x, y), glyph, font=font, fill=st["fill"], stroke_width=7, stroke_fill=st["stroke"])
        img = img.resize((128, 128), Image.LANCZOS)
        img.save(os.path.join(OUT, f"{color}{piece}.png"))

# preview sheet (not shipped)
sheet = Image.new("RGBA", (SIZE * 6, SIZE * 2), (70, 70, 74, 255))
for ri, color in enumerate("wb"):
    for ci, piece in enumerate("kqrbnp"):
        im = Image.open(os.path.join(OUT, f"{color}{piece}.png")).resize((SIZE, SIZE), Image.LANCZOS)
        sheet.alpha_composite(im, (ci * SIZE, ri * SIZE))
sheet.convert("RGB").resize((SIZE * 3, SIZE)).save("/tmp/pieces_preview.png")
print("ok", sorted(os.listdir(OUT)))
