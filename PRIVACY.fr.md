# Politique de confidentialité

Pixel Speed ne collecte aucune donnée.

## Ce que l'application lit

L'application lit les compteurs de trafic réseau du téléphone lui-même via
l'interface `TrafficStats` d'Android, en calcule une vitesse et l'affiche à
l'écran. Les compteurs ne quittent jamais le téléphone.

Avec le changement de côté activé, un service d'accessibilité lit la fenêtre de
la barre d'état : quelles icônes elle affiche et où elles se trouvent. Il ne lit
aucune autre fenêtre ni aucun contenu. Cela reste aussi sur le téléphone.

Android signale aussi au service les changements de fenêtres à l'écran, y
compris celles des autres applications. Ce signal lui indique seulement quand
regarder de nouveau la barre d'état. D'une fenêtre en haut de l'écran, il lit
seulement à quelle application elle appartient, pour reconnaître la barre
d'état. Il ne lit le contenu d'aucune fenêtre autre que la barre d'état.

L'application ne déclare aucune permission réseau, elle ne peut donc rien
envoyer nulle part. Elle ne contient ni analyse d'audience, ni service de
rapport de plantage, ni publicité, ni SDK de données tiers.

## Ce que l'application enregistre

Vos réglages (profils, position, apparence, comportement, état de pause) sont
enregistrés dans un fichier du stockage privé de l'application sur le téléphone.
La sauvegarde Android peut inclure ce fichier selon les réglages de sauvegarde
de votre appareil. Rien dedans ne vous identifie.

L'export écrit vos réglages dans un fichier que vous choisissez. L'import lit un
fichier que vous choisissez. Les deux n'ont lieu que lorsque vous le décidez.

Si l'appli plante, elle garde une trace de ce plantage dans son stockage privé :
l'heure, la version de l'appli, la trace de la pile et les dernières lignes du
journal de l'appli elle-même. Aide > Signaler un problème crée un fichier de
diagnostic à la demande : la version de l'appli, le modèle du téléphone et la
version d'Android, la géométrie de l'écran, les autorisations accordées, l'état
de l'affichage, votre export des réglages, les dernières lignes du journal de
l'appli et cette trace de plantage s'il y en a une. Il ne contient aucun compte,
identifiant d'appareil, numéro de série ni position, et rien venant d'autres
applis. L'appli ne l'envoie jamais : vous le lisez, puis vous le partagez
vous-même si vous le souhaitez.

## Permissions

Chaque permission déclarée par l'application, et à quoi elle sert :

- Afficher par-dessus d'autres applications (SYSTEM_ALERT_WINDOW) : dessine
  l'affichage.
- Notifications (POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS) : la
  notification du service de premier plan qui porte pause et reprise, et sa
  forme Live Update à partir d'Android 16.
- Service de premier plan (FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE) :
  maintient l'affichage tant que la superposition est activée.
- Service d'accessibilité (BIND_ACCESSIBILITY_SERVICE) : optionnel, désactivé
  par défaut. Lit les icônes de la barre d'état et leur position pour que
  l'affichage puisse se placer du côté de la caméra où il y a de la place. Rien
  d'autre.
- État du réseau (ACCESS_NETWORK_STATE) : indique si le téléphone est en ligne
  et quel lien est actif, pour que l'affichage montre des tirets hors ligne et
  compte la bonne interface. Ce n'est pas un accès à internet.
- Lancer au démarrage (RECEIVE_BOOT_COMPLETED) : démarre la superposition après
  un redémarrage quand vous l'activez.
- Ignorer les optimisations de batterie (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) :
  proposé sur les téléphones dont le fabricant ferme les applications en
  arrière-plan, pour que la superposition continue. Optionnel.

Il n'y a aucune permission réseau.

## Contact

Questions sur cette politique : ouvrez un ticket sur
https://github.com/zachthedev/pixel-speed/issues.

Cette politique s'applique à Pixel Speed à partir de la version 1.0.0. Ses
modifications sont publiées dans ce fichier.
