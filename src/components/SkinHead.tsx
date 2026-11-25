/**
 * Zeichnet den Kopf eines Minecraft-Skins (Gesicht + Hut-Ebene) pixelgenau auf ein Canvas.
 * Quelle ist die offizielle Skin-URL aus dem Minecraft-Profil (textures.minecraft.net).
 */
import { useEffect, useRef } from 'react';

export function SkinHead({ url, size = 32, className = '' }: { url?: string; size?: number; className?: string }) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;
    ctx.imageSmoothingEnabled = false;
    ctx.clearRect(0, 0, 8, 8);

    // Platzhalter (Steve-ähnliche Farben), solange kein Skin geladen ist
    const placeholder = () => {
      ctx.fillStyle = '#1fd35f';
      ctx.fillRect(0, 0, 8, 8);
      ctx.fillStyle = '#2eff7a';
      ctx.fillRect(1, 3, 2, 1);
      ctx.fillRect(5, 3, 2, 1);
    };
    if (!url) return placeholder();

    const img = new Image();
    img.onload = () => {
      ctx.clearRect(0, 0, 8, 8);
      ctx.drawImage(img, 8, 8, 8, 8, 0, 0, 8, 8); // Gesicht
      ctx.drawImage(img, 40, 8, 8, 8, 0, 0, 8, 8); // Hut-Ebene
    };
    img.onerror = placeholder;
    img.src = url;
  }, [url]);

  return (
    <canvas
      ref={ref}
      width={8}
      height={8}
      className={`rounded-[3px] ${className}`}
      style={{ width: size, height: size, imageRendering: 'pixelated' }}
    />
  );
}
