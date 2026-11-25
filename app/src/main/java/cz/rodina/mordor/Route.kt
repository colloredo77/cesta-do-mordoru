package cz.rodina.mordor

import kotlin.math.min

/**
 * Zastávka na cestě. x, y = poloha na vlastní stylizované mapě (0..1), km = vzdálenost od startu.
 * Hlavní zastávky mají delší příběh, mezizastávky (minor) jen krátký popis.
 * Texty jsou převyprávěné vlastními slovy.
 */
data class Waypoint(
    val name: String,
    val km: Double,
    val x: Float,
    val y: Float,
    val note: String,
    val story: String = note,
    val minor: Boolean = false,
)

object Route {
    val majors: List<Waypoint> = listOf(
        Waypoint(
            "Dno pytle", 0.0, .10f, .30f, "Výprava vyráží z Hobitína. Dobrodružství začíná za prahem.",
            "Útulná hobití nora pod Pahorkem. Frodo tu po strýci Bilbovi zdědil nejen domov, ale i kouzelný prsten, " +
                "který se ukázal být Jedním prstenem Temného pána. Gandalf radí: prsten musí pryč z Kraje, a to co nejdřív. " +
                "Výprava vyráží potichu, s batohy plnými jídla a se Samem, který svého pána nechce opustit.",
        ),
        Waypoint(
            "Rádovsko", 64.0, .165f, .315f, "Přes Brandyvínu na pramici. Poslední klidný nocleh.",
            "Na východním břehu Brandyvíny žijí Brandorádi – hobiti, kteří se nebojí lodí ani sousedství Starého hvozdu. " +
                "Smíšek tu pro přátele připravil dům, kde si naposledy dopřejí horkou koupel a pořádnou večeři. " +
                "Ukáže se, že Smíšek s Pipinem o tajemství prstenu dávno vědí – a Froda samotného nepustí.",
        ),
        Waypoint(
            "Starý hvozd", 85.0, .20f, .33f, "Stromy tu šeptají a pěšiny se samy stáčejí.",
            "Prastarý les, jehož stromy pamatují dobu dávno před hobity a cizince nemají rády. " +
                "Pěšiny se stáčejí, kam chtějí, a svádějí výpravu k řece Vrbovce. Tam hobity zajme stará vrba " +
                "a zachrání je až podivuhodný Tom Bombadil – veselý pán lesa, na kterého prsten nemá žádnou moc.",
        ),
        Waypoint(
            "Mohylové vrchy", 130.0, .23f, .30f, "Mlha nad mohylami. Raději nezastavovat.",
            "Travnaté kopce posázené mohylami králů dávno zaniklé říše. Hobiti tu v mlze zabloudí a padnou do moci " +
                "zlých duchů mohyl. Frodo najde odvahu, zpěvem přivolá Toma Bombadila a ten je vysvobodí. " +
                "Z mohyly si odnesou staré dýky, které se jim ještě budou hodit.",
        ),
        Waypoint(
            "Hůrka", 233.0, .27f, .29f, "Hostinec U Skákavého poníka – teplé jídlo a cizinec v koutě.",
            "První opravdové městečko za hranicemi Kraje, kde vedle sebe žijí lidé i hobiti. V hostinci U Skákavého poníka " +
                "se Frodovi prsten nešťastně navlékne na prst přímo před hosty. V koutě sedí zachmuřený hraničář zvaný Chodec – " +
                "Gandalf mu v dopise důvěřuje, a od té chvíle vede výpravu on.",
        ),
        Waypoint(
            "Větrov", 330.0, .33f, .275f, "Rozvaliny strážní věže na kopci. Noc je tu nebezpečná.",
            "Holý kopec s rozvalinami staré strážní věže, odkud je vidět široko daleko. Právě tady výpravu v noci přepadne " +
                "pět Černých jezdců. Frodo si v panice navlékne prsten a jejich vůdce ho zraní morgulskou čepelí. " +
                "Rána je ledová a úlomek zůstane v těle – do Roklinky je to závod s časem.",
        ),
        Waypoint(
            "Poslední most", 420.0, .39f, .28f, "Odtud už žádné pohodlné cesty.",
            "Kamenný most přes řeku Mšenou je poslední stavbou na Velké východní cestě, o kterou se ještě někdo stará. " +
                "Chodec na něm najde zelený elfí kámen – znamení, že tudy prošli elfové a cesta je zatím bezpečná. " +
                "Dál vedou jen divoké stezky.",
        ),
        Waypoint(
            "Brod Bruinenu", 640.0, .455f, .27f, "Řeka chrání Roklinku – rychle na druhý břeh!",
            "Řeka Bruinen je hranicí Roklinky. Elf Glorfindel posadí zraněného Froda na svého bílého koně a ten s ním uhání " +
                "k brodu, Černí jezdci v patách. Když jezdci vjedou do vody, řeka se vzedme v divokou povodeň s vlnami " +
                "jako bílí koně a smete je. (V této hře tu Černý jezdec opravdu zmizí!)",
        ),
        Waypoint(
            "Roklinka", 735.0, .48f, .255f, "Útočiště elfů. Odpočinek, hostina a rada.",
            "Skryté údolí elfa Elronda. Frodo se tu uzdraví a znovu se setká s Bilbem. Na velké radě padne rozhodnutí, " +
                "že prsten musí být zničen v ohni Hory osudu, a vznikne Společenstvo devíti: čtyři hobiti, Gandalf, " +
                "Aragorn, Legolas, Gimli a Boromir.",
        ),
        Waypoint(
            "Cesmínov", 940.0, .465f, .375f, "Opuštěný kraj pod horami. Nebe hlídají ptáci.",
            "Opuštěný kraj, kde kdysi elfští kováři kuli prsteny moci. Dnes tu rostou jen cesmíny a nad krajinou krouží " +
                "hejna havranů, kteří výpravu vyzvídají. Společenstvo se odtud pokusí přejít hory zasněženým průsmykem.",
        ),
        Waypoint(
            "Morie", 1150.0, .505f, .43f, "Dlouhá tma pod horami. Tiše a rychle.",
            "Obří podzemní říše trpaslíků pod Mlžnými horami, kdysi plná světla a zpěvu, dnes temná a opuštěná. " +
                "Výprava ji musí projít, když ji hora nepustí přes průsmyk. V hlubinách se probudí plamenný démon " +
                "starého světa a Gandalf se mu postaví na úzkém mostě, aby ostatní mohli utéct.",
        ),
        Waypoint(
            "Lothlórien", 1240.0, .565f, .47f, "Zlatý les, kde čas plyne jinak.",
            "Zlatý les paní Galadriel, kde stromy mají stříbrné kmeny a zlaté listí. Společenstvo tu truchlí pro Gandalfa " +
                "a nabírá síly. Výprava dostane dary na cestu – elfí pláště, provazy a lembas, elfí chléb, " +
                "ze kterého je poutník sytý celý den. (Lembas můžete ve hře získávat i vy!)",
        ),
        Waypoint(
            "Amon Hen", 1866.0, .61f, .645f, "Po řece až k Rauros. Tady se výprava rozdělí.",
            "Kopec Zraku nad vodopády Rauros. Boromir tu podlehne touze po prstenu a pokusí se ho Frodovi vzít. " +
                "Frodo pochopí, že prsten rozvrací Společenstvo zevnitř, a rozhodne se jít do Mordoru sám. " +
                "Sam ho ale neopustí – brodí se za ním k loďce, i když neumí plavat.",
        ),
        Waypoint(
            "Emyn Muil", 1950.0, .66f, .625f, "Bludiště ostrých skal. Někdo vás sleduje…",
            "Bludiště ostrých šedých skal a roklí, ze kterého Frodo a Sam několik dní nemohou najít cestu ven. " +
                "Jedné noci zjistí, že je někdo sleduje – po skalní stěně šplhá hubený tvor se svítícíma očima. " +
                "Glum je chycen a přísahá, že je dovede k Mordoru.",
        ),
        Waypoint(
            "Mrtvé bažiny", 2120.0, .705f, .605f, "Nedívejte se na světýlka ve vodě.",
            "Rozlehlé mokřady, kde se kdysi bojovala velká bitva. Nad hladinou tančí bledá světýlka a ve vodě se zjevují " +
                "tváře. Glum vede hobity po skrytých stezkách a varuje je, ať se na světla nedívají. " +
                "Vysoko nad nimi poprvé přelétne okřídlený Nazgûl.",
        ),
        Waypoint(
            "Morannon", 2250.0, .755f, .59f, "Černá brána je zavřená. Musí existovat jiná cesta.",
            "Černá brána Mordoru mezi dvěma strážními věžemi, hlídaná nespočtem skřetů. Projít tudy je nemožné. " +
                "Glum prozradí jinou cestu – na jih, přes horský průsmyk. Frodo mu musí uvěřit, " +
                "i když Sam má o Glumových úmyslech vážné pochybnosti.",
        ),
        Waypoint(
            "Henneth Annûn", 2440.0, .70f, .70f, "Skrytý úkryt za vodopádem v Ithilienu.",
            "Skrytá jeskyně za vodopádem, úkryt gondorských hraničářů. Jejich velitel Faramir, Boromirův bratr, " +
                "se dozví o prstenu, ale odolá pokušení. Hobity propustí a přidá jim jídlo na cestu.",
        ),
        Waypoint(
            "Křižovatka", 2520.0, .715f, .755f, "Kamenný král s korunou z květů.",
            "Rozcestí v lese se starou kamennou sochou krále, které kdosi urazil hlavu. Hlava leží v trávě " +
                "a obtáčí ji kvetoucí rostlina jako koruna. Frodo to v posledním paprsku slunce bere jako znamení naděje: " +
                "zlo nemůže vládnout navždy.",
        ),
        Waypoint(
            "Minas Morgul", 2560.0, .74f, .775f, "Bledé město. Nedívat se nahoru.",
            "Kdysi gondorská pevnost, dnes bledé, strašidelné město, kde sídlí Nazgûlové. Z brány právě vyráží " +
                "obrovské vojsko proti Gondoru a vede ho sám Pán nazgûlů. Frodo cítí, jak ho prsten táhne k tomu místu, " +
                "a jen s námahou pokračuje dál.",
        ),
        Waypoint(
            "Kirith Ungol", 2610.0, .765f, .745f, "Schody, tunel a něco ve tmě…",
            "Průsmyk nad Minas Morgulem, kam vedou nekonečné schody a temný tunel. Glum sem hobity zavede schválně – " +
                "v tunelu žije obří pavoučice. Frodo je ochromen a skřeti ho odnesou. Sam, přesvědčený, " +
                "že jeho pán zemřel, si vezme prsten a jde ho zachránit.",
        ),
        Waypoint(
            "Hora osudu", 2863.0, .86f, .70f, "Cíl! Prsten skončil v ohni. Středozem je zachráněna.",
            "Sopka uprostřed Mordoru, kde byl prsten vykován – jediné místo, kde ho lze zničit. Sam vynese vyčerpaného " +
                "Froda až nahoru. Na okraji propasti však Frodo prsten odmítne vydat. Rozhodne to až Glum, který mu ho " +
                "vyrve a i s ním spadne do ohně. Temný pán padá a Středozem je volná.",
        ),
    )

    private val minorsRaw: List<Triple<String, Double, String>> = listOf(
        Triple("Zelené vrchy", 22.0, "Mírné kopce Kraje. Za soumraku kolem projede tichý jezdec v černém a čenichá…"),
        Triple("Lesní konec", 42.0, "Hobiti tu potkají putující elfy a stráví s nimi noc pod hvězdami."),
        Triple("Rádovský přívoz", 58.0, "Pramice přes Brandyvínu – na druhém břehu už jsou v bezpečí… prozatím."),
        Triple("Vrbovka", 100.0, "Líná řeka ve Starém hvozdu. Pozor na starou vrbu, umí uspat."),
        Triple("Bombadilův dům", 112.0, "Teplý domov Toma Bombadila a krásné Zlatěnky. Dva dny odpočinku a písní."),
        Triple("Velká východní cesta", 175.0, "Konečně pořádná cesta. Do Hůrky už to není daleko."),
        Triple("Komáří bažiny", 272.0, "Mokřady, kde hobity dva dny žerou komáři. Nikdo neví, čím se živí, když tu nejsou hobiti."),
        Triple("Kamenní trollové", 470.0, "Tři zkamenělí trollové z Bilbova dobrodružství. Sam o nich na místě složí písničku."),
        Triple("Trollí hvozdy", 540.0, "Divoké kopce bez cest. Chodec hledá stopy a Frodova rána bolí čím dál víc."),
        Triple("Úpatí hor", 840.0, "Společenstvo opouští Roklinku a míří na jih podél hor, zahalené v nenápadných pláštích."),
        Triple("Caradhras", 1010.0, "Krutá hora se zasněženým průsmykem. Sněhová bouře výpravu zažene zpátky."),
        Triple("Dveře Durinovy", 1120.0, "Skrytá brána do Morie. Otevře ji jen správné slovo – stačí říct „příteli“ elfsky."),
        Triple("Most Khazad-dûmu", 1180.0, "Úzký kamenný most nad propastí. Tady se Gandalf postaví démonovi."),
        Triple("Nimrodel", 1222.0, "Průzračná říčka na hranici Lórienu. Legolas o ní zpívá smutnou píseň."),
        Triple("Odplutí z Lórienu", 1310.0, "Tři elfí loďky, šedé pláště a lembas v batozích. Plujeme po Anduině."),
        Triple("Hnědé pláně", 1480.0, "Pusté, holé břehy řeky. Někdo za nimi pluje na kmeni…"),
        Triple("Peřeje Sarn Gebir", 1700.0, "Nebezpečné peřeje – loďky se musí přenést po souši."),
        Triple("Argonath", 1800.0, "Obří kamenní králové hlídají řeku s rukama zdviženýma: dál nechoďte!"),
        Triple("Sestup ze skal", 2010.0, "Slaňování po elfím provazu ze Samova batohu. Provaz se pak sám rozváže!"),
        Triple("Pustina před branou", 2200.0, "Zničená krajina plná strusky a jedovatých jezírek."),
        Triple("Ithilienské háje", 2350.0, "Voňavé byliny a lesy. Sam tu uvaří králíka – Glum ho chtěl syrového."),
        Triple("Přímé schody", 2585.0, "Nekonečné schody vytesané ve skále. Nohy pálí, ale zpátky to nejde."),
        Triple("Věž Kirith Ungol", 2650.0, "Skřetí věž nad průsmykem. Sam tu hledá svého pána za zpěvu písničky."),
        Triple("Planina Gorgoroth", 2730.0, "Popel, prach a žízeň. Každý krok je dřina, ale Hora osudu už je blízko."),
        Triple("Úpatí Hory osudu", 2820.0, "Poslední stoupání. Prsten za Froda Sam nést nemůže – ale Froda samotného ano, a vezme ho na záda."),
    )

    /** Všechny zastávky (hlavní + mezizastávky) seřazené podle km. */
    val points: List<Waypoint> by lazy {
        (majors + minorsRaw.map { (name, km, note) ->
            val (x, y) = positionAt(km)
            Waypoint(name, km, x, y, note, note, minor = true)
        }).sortedBy { it.km }
    }

    val totalKm: Double get() = majors.last().km

    /** Poloha na mapě – cesta vede po hlavních zastávkách. */
    fun positionAt(km: Double): Pair<Float, Float> {
        val p = majors
        if (km <= 0.0) {
            // nepřátelé před startem čekají jihozápadně od Hobitína
            val f = (min(-km, 80.0) / 80.0).toFloat()
            return (p[0].x - 0.06f * f) to (p[0].y + 0.16f * f)
        }
        for (i in 1 until p.size) {
            if (km <= p[i].km) {
                val a = p[i - 1]
                val b = p[i]
                val t = ((km - a.km) / (b.km - a.km)).toFloat()
                return (a.x + (b.x - a.x) * t) to (a.y + (b.y - a.y) * t)
            }
        }
        return p.last().x to p.last().y
    }

    fun lastReached(km: Double): Waypoint = points.last { it.km <= km.coerceAtLeast(0.0) }
    fun next(km: Double): Waypoint? = points.firstOrNull { it.km > km }
    fun nextMajor(km: Double): Waypoint? = majors.firstOrNull { it.km > km }
    fun byName(name: String): Waypoint? = points.firstOrNull { it.name == name }
}
