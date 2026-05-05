import re

with open("backend-cartes/src/test/java/fr/enseeiht/jeux/TarotTest.java", "r") as f:
    content = f.read()

content = content.replace("import fr.enseeiht.jeux.dto.EtatJeuTarotDTO;", "import fr.enseeiht.jeux.tarot.EtatTarotDTO;")
content = content.replace("import fr.enseeiht.jeux.service.TarotScoringService;", "import fr.enseeiht.jeux.tarot.TarotScoringService;")
content = content.replace("import fr.enseeiht.jeux.service.TarotService;", "import fr.enseeiht.jeux.tarot.TarotService;")
content = content.replace("EtatJeuTarotDTO", "EtatTarotDTO")

with open("backend-cartes/src/test/java/fr/enseeiht/jeux/TarotTest.java", "w") as f:
    f.write(content)
