# 🚛 Scania Driver — Implementation Plan

## Stos technologiczny
- Java 8 (JDK 8u202)
- JOGL (jogamp-all-platforms) — GL2, immediate mode (`glBegin/glEnd`)
- Eclipse IDE
- Baza: styl i struktura z `p01.java` (plik poglądowy), główny plik to `main.java`

---

## Architektura kodu

```
main.java (główna klasa)
├── main()
├── JFrame + GLCanvas + GLEventListener
├── init()       — inicjalizacja GL, generowanie świata
├── display()    — główna pętla renderowania
├── reshape()    — obsługa resize okna
└── KeyListener  — sterowanie

HeightMap.java
├── float[][] grid        — siatka wysokości
├── generate()            — generowanie szumem Perlina
└── getHeight(x, z)       — interpolacja bilinearna

Truck.java
├── float x, y, z         — pozycja
├── float speed, angle    — prędkość i kierunek
├── float tilt            — pochylenie wg terenu
├── update(heightMap)     — fizyka / terrain following
└── draw(gl)              — rysowanie z prymitywów

Tree.java
├── float x, y, z
├── float height, radius
└── draw(gl)

World.java
├── List<Tree> trees
├── Road road
├── generateTrees()
└── draw(gl)
```

---

## Etapy implementacji

### Etap 1 — Heightmapa i teren
- Implementacja prostego szumu Perlina (własna klasa, ~50 linii)
- Generowanie siatki `float[100][100]`
- Rysowanie terenu jako `GL_TRIANGLES`
- Kolorowanie wg wysokości:
  - `y < 0.1` → niebieski (woda)
  - `0.1 < y < 0.5` → zielony (trawa)
  - `0.5 < y < 0.8` → szary (skały)
  - `y > 0.8` → biały (śnieg)
- Normalne per-trójkąt do oświetlenia (`glNormal3f`)

### Etap 2 — Scania (model szczegółowy, sam ciągnik siodłowy)

Cały model w metodzie `draw(GL2 gl)` klasy `Truck.java`, złożony z prymitywów GL2.
Szacowany rozmiar: ~250-300 linii.

#### Główna bryła
- **Kabina** — główny prostopadłościan (`GL_QUADS`), lekko wyższy z przodu
- **Daszek kabiny** — płaski prostopadłościan na dachu (charakterystyczny dla Scanii)
- **Spoiler dachowy** — trapezoidalna bryła za kabiną
- **Zderzak przedni** — szeroki, niski prostopadłościan
- **Stopnie wejściowe** — małe schodki po bokach pod drzwiami

#### Przód
- **Maska silnika** — prostopadłościan wystający przed kabinę, lekko pochylony
- **Grill** — ciemny prostokąt z poziomymi liniami (`GL_LINES`)
- **Logo Scania** — żółty/złoty romb na grillu (`GL_QUADS`)
- **Reflektory główne** — prostokątne, białe (`GL_QUADS`)
- **Światła DRL** — wąskie poziome paski pod reflektorami
- **Kierunkowskazy** — pomarańczowe prostokąty w narożnikach

#### Boki
- **Drzwi** — lekko wystający prostokąt z zaznaczoną klamką
- **Lusterka** — prostopadłościan na wysięgniku, po obu stronach kabiny
- **Zbiorniki paliwa** — walce (`GL_QUAD_STRIP`) po bokach pod kabiną
- **Osłony przeciwbłotne** — ciemne prostokąty nad tylnymi kołami

#### Tył
- **Światła tylne** — czerwone prostokąty (`GL_QUADS`)
- **Światła cofania** — białe prostokąty
- **Rura wydechowa** — pionowy walec (`GL_QUAD_STRIP`) po prawej stronie kabiny
- **Złącze siodłowe** — płyta na tylnej ramie (`GL_QUADS`)

#### Koła i podwozie
- **6 kół** — przednia oś (2) + tylna oś podwójna tandem (4)
- Każde koło: wielokąt (`GL_POLYGON`) z zaznaczoną piastą i śrubami (`GL_LINES`)
- **Rama podwozia** — dwie poziome belki wzdłuż całej długości (`GL_QUADS`)
- **Amortyzatory** — walce między ramą a osią

#### Detale na dachu
- **Klakson pneumatyczny** — dwa małe walce na dachu
- **Antena** — cienki patyk (`GL_LINES`)
- **Lampki obrysowe** — małe żółte/białe kropki na krawędziach dachu (`GL_POINTS`)

### Etap 3 — Kamera
- Kamera śledząca pojazd (widok z tyłu/góry)
  ```java
  glu.gluLookAt(
      truckX - dx*dist, truckY + camHeight, truckZ - dz*dist,
      truckX, truckY, truckZ,
      0, 1, 0
  );
  ```
- Obrót kamery myszą (MouseMotionListener) — pitch i yaw
- Opcjonalnie klawisz `C` — przełączenie na widok z kabiny

### Etap 4 — Sterowanie i terrain following
- `KeyListener` — klawisze `↑ ↓ ← →`
- Aktualizacja pozycji pojazdu każdą klatką
- `getHeight(x, z)` — interpolacja bilinearna w siatce:
  1. Znajdź indeks kafelka pod pojazdem
  2. Weź 4 narożniki
  3. Interpoluj Y bilinearnie
- Pojazd unosi się i opada wraz z terenem
- Pochylenie Scanii zgodnie z normalną terenu (`glRotatef`)
- Animacja kół — kąt obrotu proporcjonalny do prędkości

### Etap 5 — Drzewa
- Klasa `Tree` z polami `x, y, z, height, radius, greenShade`
- Generowanie przy starcie: 300 losowych pozycji `(x, z)`
- `y` z `getHeight(x, z)` — drzewo stoi na terenie
- Odrzucanie punktów zbyt blisko drogi
- Model drzewa:
  - Pień — `GL_QUAD_STRIP` (walec)
  - Korona — 2 stożki nałożone (`GL_TRIANGLE_FAN`)
  - Losowy odcień zieleni + losowy obrót Y

### Etap 6 — Oświetlenie
- `glEnable(GL_LIGHTING)` + `GL_LIGHT0` jako słońce
- Pozycja światła zależna od pory dnia
- `glEnable(GL_COLOR_MATERIAL)` — kolory reagują na światło
- Normalne na terenie i bryle pojazdu

### Etap 7 — Efekty wizualne
- **Mgła** (`glFog`) — gęstnieje z odległością, kolor dopasowany do pory dnia
- **Deszcz** — lista ~500 cząsteczek (`GL_LINES`), losowe pozycje, opadają i resetują się
- **Zmiana pory dnia** — klawisz `T` cykluje: dzień → zachód → noc
  - Zmiana `glClearColor` nieba
  - Zmiana pozycji i koloru `GL_LIGHT0`
  - Nocą drzewa i teren ciemniejsze
- **Animacja zawieszenia** — drobne losowe drgania Y pojazdu (`sin(time)*0.005`)
- **Światła hamowania** — czerwone prostokąty rozświetlają się przy `↓`

### Etap 8 — Droga
- Płaski pas biegnący przez mapę (wzdłuż osi Z lub krzywy)
- Animowane linie środkowe — przesuwają się przy jeździe (złudzenie ruchu)
- Teren wokół drogi wyrównany (opcjonalnie)
- Barierki / słupki co kilka jednostek po bokach

---

## Efekty wizualne — podsumowanie

| Efekt | Trudność | Priorytet |
|---|---|---|
| Kolorowanie terenu wg wysokości | ⭐ | must-have |
| Obracające się koła | ⭐ | must-have |
| Oświetlenie GL_LIGHT | ⭐⭐ | must-have |
| Mgła glFog | ⭐ | must-have |
| Pochylenie pojazdu na skosie | ⭐⭐ | must-have |
| Zmiana pory dnia | ⭐⭐ | nice-to-have |
| Deszcz (cząsteczki) | ⭐⭐ | nice-to-have |
| Animacja zawieszenia | ⭐ | nice-to-have |
| Światła hamowania | ⭐ | nice-to-have |
| Obrót kamery myszą | ⭐⭐ | nice-to-have |

---

## Kolejność pracy (sugerowana sesja)

```
[1] Heightmapa + teren widoczny na ekranie
[2] Scania stojąca na terenie
[3] Sterowanie + terrain following + kamera
[4] Drzewa
[5] Oświetlenie + mgła
[6] Efekty (deszcz, pory dnia, zawieszenie)
[7] Droga + detale
```
