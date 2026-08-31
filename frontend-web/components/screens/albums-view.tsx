"use client";

import { useState } from "react";
import { ChevronRight, Plus } from "lucide-react";

import { Card, Header } from "@/components/layout/app-navigation";
import type { ActionKind } from "@/types/actions";

const albums = [
  {
    name: "Verano 2026",
    description: "Los mejores momentos de verano",
    pet: "Fido",
    photos: [
      "https://images.unsplash.com/photo-1552053831-71594a27632d?auto=format&fit=crop&w=700&q=80",
      "https://images.unsplash.com/photo-1507146426996-ef05306b995a?auto=format&fit=crop&w=700&q=80",
      "https://images.unsplash.com/photo-1558788353-f76d92427f16?auto=format&fit=crop&w=700&q=80",
      "https://images.unsplash.com/photo-1477884213360-7e9d7dcc1e48?auto=format&fit=crop&w=700&q=80",
    ],
  },
  {
    name: "Cachorrito",
    description: "De cuando era bebé",
    pet: "Fido",
    photos: [
      "https://images.unsplash.com/photo-1518717758536-85ae29035b6d?auto=format&fit=crop&w=700&q=80",
      "https://images.unsplash.com/photo-1530281700549-e82e7bf110d6?auto=format&fit=crop&w=700&q=80",
      "https://images.unsplash.com/photo-1548199973-03cce0bbc87b?auto=format&fit=crop&w=700&q=80",
    ],
  },
  {
    name: "Luna en su casa",
    description: "Siestas y aventuras",
    pet: "Luna",
    photos: [
      "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=700&q=80",
      "https://images.unsplash.com/photo-1573865526739-10659fec78a5?auto=format&fit=crop&w=700&q=80",
      "https://images.unsplash.com/photo-1495360010541-f48722b34f7d?auto=format&fit=crop&w=700&q=80",
    ],
  },
];

export function AlbumsView({ open }: { open: (kind: ActionKind) => void }) {
  const [selected, setSelected] = useState<number | null>(null);

  if (selected !== null) {
    const album = albums[selected];
    return (
      <>
        <Header
          title={album.name}
          subtitle={`${album.pet} · ${album.photos.length} fotos`}
          onBack={() => setSelected(null)}
          action={<button className="icon-btn" aria-label="Agregar foto"><Plus /></button>}
        />
        <p className="album-detail-copy">{album.description}</p>
        <div className="photo-grid">
          {album.photos.map((src, index) => (
            <button key={src} className={index === 0 ? "featured" : ""} aria-label={`Ver foto ${index + 1}`}>
              <img src={src} alt={`${album.name}, foto ${index + 1}`} />
              <span>{index + 1}</span>
            </button>
          ))}
        </div>
      </>
    );
  }

  return (
    <>
      <Header
        title="Álbumes"
        subtitle="Tus recuerdos favoritos"
        action={<button className="icon-btn" onClick={() => open("album")} aria-label="Crear álbum"><Plus /></button>}
      />
      <div className="album-list">
        {albums.map((album, index) => (
          <button className="album-card-button" key={album.name} onClick={() => setSelected(index)}>
            <Card>
              <div className="album-art"><img src={album.photos[0]} alt="" /></div>
              <div><h3>{album.name}</h3><p>{album.description}</p><span>{album.pet}</span></div>
              <small>{album.photos.length} fotos</small>
              <ChevronRight />
            </Card>
          </button>
        ))}
      </div>
    </>
  );
}
