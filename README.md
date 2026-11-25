# Cesta do Mordoru

Rodinná Android aplikace. Kroky z hodinek (Garmin, Xiaomi) nebo zadané ručně posouvají každého člena výpravy po cestě z Hobitína k Hoře osudu (2 863 km). Za výpravou jde Glum a jeho tempo se řídí skutečným počasím ve vašem městě: v dešti a mlze jde rychleji, za slunce pomaleji. Když dožene posledního z výpravy, vrátí ho o 10 km.

## 1. Firebase (jednou, zdarma, asi 10 minut, nejlépe na počítači)

1. Na https://console.firebase.google.com klikněte na **Přidat projekt** (třeba „cesta-do-mordoru“). Google Analytics můžete vypnout.
2. **Přidat aplikaci → Android**, název balíčku: `cz.rodina.mordor`. Ostatní pole nechte prázdná.
3. Stáhněte soubor **google-services.json** a mějte ho po ruce.
4. **Build → Authentication → Začít → Sign-in method → Anonymní → Povolit.**
5. **Build → Firestore Database → Vytvořit databázi** (umístění `eur3`, režim *produkční*).
6. Ve Firestore otevřete záložku **Pravidla**, vložte obsah souboru `firestore.rules` z tohoto projektu a dejte **Publikovat**.

## 2. GitHub

1. Vytvořte nový repozitář, třeba `cesta-do-mordoru`. Doporučuji **Private**.
2. Nahrajte do něj obsah tohoto ZIPu (Add file → Upload files, přetáhněte všechny soubory a složky).
   - Složka `.github` je skrytá. Pokud se nenahraje, vytvořte soubor ručně přes **Add file → Create new file**, jako název napište `.github/workflows/build.yml` a vložte obsah stejnojmenného souboru ze ZIPu.
3. **Settings → Secrets and variables → Actions → New repository secret**
   - Name: `GOOGLE_SERVICES_JSON`
   - Secret: zkopírujte sem **celý obsah** souboru google-services.json.
4. **Actions → Sestavit APK → Run workflow.** Za 5–8 minut najdete hotovou aplikaci v **Releases** jako `CestaDoMordoru.apk`.

Každá další změna kódu v repozitáři sestaví novou verzi a ta se nainstaluje přes starou.

## 3. Telefony

1. Na každém telefonu otevřete Releases (přihlášeni na GitHubu), stáhněte APK a nainstalujte ho. Instalaci z prohlížeče je potřeba povolit.
   - Na dětském telefonu s Family Link musí instalaci schválit rodič.
2. První člověk dá **Založit výpravu** a zadá město. Ostatní dají **Připojit se** a zadají 6místný kód (najdete ho v Nastavení → Poslat kód rodině).
3. Každý si vybere jméno, postavu, fotku a délku kroku.
4. Na záložce **Výprava** klepněte na **Načíst moje kroky z hodinek** a povolte čtení kroků.

### Napojení hodinek na Health Connect
- **Garmin Connect:** Více → Nastavení → Propojené aplikace → Health Connect → povolit Kroky.
- **Xiaomi Mi Fitness:** v Profilu najděte propojení s Health Connect (název položky se liší podle verze) a povolte zápis kroků.
- Pokud telefon počítá kroky i sám, nastavte v Health Connect → Data a přístup → Aktivita → Kroky → Zdroje a priorita hodinky na první místo.
- Aplikace sama načte kroky při každém otevření, zpětně až 30 dní.

## Aktualizace

- Novou verzi nahrajete na GitHub (Add file → Upload files, přetáhnout složku `app` z nového ZIPu → Commit changes). Sestaví se sama.
- Aplikace si jednou za hodinu (a v Nastavení → Zkontrolovat aktualizace) zjistí, jestli je na GitHubu novější verze, a ukáže lištu **Stáhnout**. Stažený soubor stačí otevřít a dát Instalovat – data zůstanou.
- Kontrola funguje jen u **veřejného** repozitáře: Settings → General → úplně dole Danger Zone → Change visibility → Public. Kód pak uvidí kdokoli, ale klíč k Firebase (secret) zůstává skrytý a do vaší výpravy se bez 6místného kódu nikdo nedostane.

## Nepřátelé
- **Glum** hlídá toho, kdo je nejvíc vzadu. Déšť a mlha ho zrychlují, slunce, sníh a horko zpomalují. Když dožene, vrací o 10 km.
- **Nazgûl** jde po Prstenonošovi (postava Frodo Pytlík; když ji nikdo nemá, po tom, kdo je nejdál). Zataženo a dlouhé noci ho zrychlují, slunce a déšť zpomalují. Když dožene, Prstenonoš je 3 dny zraněný (počítá se polovina kroků).
  - Fáze 1: Černý jezdec do Brodu Bruinenu (tam ho smete povodeň). Fáze 2: okřídlený od Mrtvých bažin do Mordoru.
- Podrobnosti a předpověď na dnešní noc jsou na záložce **Nepřátelé** nebo po klepnutí na žeton na mapě.

## Příběh, lembas, odznaky, statistiky
- Každá zastávka má krátký příběh – klepněte na ni na mapě nebo na zápis v Kronice. Když ji sami dosáhnete, vyskočí jako nová kapitola. Mezi hlavními zastávkami je 25 menších mezizastávek.
- **Lembas** dostanete za den s aspoň 15 000 kroky (hranice v Nastavení). Na záložce Výprava ho můžete použít: vyléčit zranění od Nazgûla, nebo zastavit Gluma na jednu noc.
- **Odznaky** (17 druhů) – na kartě člena na záložce Výprava, nové se ukážou v ranním hlášení.
- **Statistiky** – Výprava → Statistiky: grafy 7/30 dní, žebříček týdne, průměry a nejlepší dny.

## Automatické nahrávání a ranní hlášení
- Kolem 22:00 aplikace sama nahraje kroky (pokud je v Nastavení povoleno čtení na pozadí), jinak pošle připomínku.
- Ráno po otevření ukáže **Co se stalo v noci**.

## Jak hra funguje
- Kroky × délka kroku = kilometry. Ruční zadání má přednost před hodinkami a dá se zadat i za ostatní.
- Glum začíná 25 km za výpravou. Každou noc se pohne podle včerejšího počasí (Open-Meteo). Základní tempo se nastavuje v Nastavení.
- Když Glum dožene posledního člena, ten se vrátí o 10 km a Glum ustoupí o 15 km.
- Všechno se přepočítává z uložených kroků. Kroky zadané později se proto projeví i zpětně.
- Mapa je vlastní stylizovaná kresba a nekopíruje Tolkienovu mapu. Neprozkoumané kraje zakrývá mlha, která se odhaluje, jak výprava postupuje. Mapu jde přibližovat dvěma prsty nebo tlačítky +/−, tlačítko ◎ vrátí pohled k výpravě.
- V Nastavení jde cestu **začít znovu** (všichni zpět do Hobitína, profily zůstanou) nebo výpravu **smazat** úplně (na všech telefonech).
