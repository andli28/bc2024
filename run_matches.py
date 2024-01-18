from itertools import product
import subprocess

import platform

emojiMode = True
emojiMap = {
    'Won': ':heavy_check_mark:',
    'Lost': ':x:',
    'Tied': ':grimacing:',
    'N/A': ':heavy_minus_sign:',
    'Error': ':heavy_exclamation_mark:'
}
errors = []
currentBot = 'mainbot'

bots = ['sprint1']
botsSet = set(bots)

sprint1Maps = ['AceOfSpades', 'Alien', 'Ambush', 'Battlecode24', 'BigDucksBigPond', 'Canals', 'CH3353C4K3F4CT0RY',
    'Duck', 'Fountain', 'Hockey', 'MazeRunner', 'Rivers', 'Snake', 'Soccer', 'SteamboatMickey', 'Yinyang']
initialMaps = ['DefaultHuge', 'DefaultLarge', 'DefaultMedium', 'DefaultSmall']
customMaps = ['pathfinder', 'bridge_aquifer', 'buh', 'crashtest1', 'Maze', 'one_on_one', 'pathfinder2', 'two_moats',
              'kirby', 'diagonal_grid']

maps = initialMaps #+ sprint1Maps # + customMaps
mapsSet = set(maps)

matches = set(product(bots, maps))

numWinsMapping = {
    0: 'Lost',
    1: 'Tied',
    2: 'Won',
}

totalWins = 0

def retrieveGameLength(output):
    startIndex = output.find('wins (round ')
    if startIndex == -1:
        return -1
    endIndex = output.find(')', startIndex)
    if endIndex == -1:
        return -1
    return output[startIndex + len('wins(round ') + 1:endIndex]

def run_match(bot, map, toAddWins):
    print("Running {} vs {} on {}".format(currentBot, bot, map))
    try:
        if platform.system() == 'Windows':
            # for local windows testing
            outputA = str(subprocess.check_output(['gradlew', 'run', '-PteamA=' + currentBot, '-PteamB=' + bot, '-Pmaps=' + map], shell=True))
            outputB = str(subprocess.check_output(['gradlew', 'run', '-PteamA=' + bot, '-PteamB=' + currentBot, '-Pmaps=' + map], shell=True))
        elif platform.system() == 'Linux':
            outputA = str(subprocess.check_output(['./gradlew', 'run', '-PteamA=' + currentBot, '-PteamB=' + bot, '-Pmaps=' + map]))
            outputB = str(subprocess.check_output(['./gradlew', 'run', '-PteamA=' + bot, '-PteamB=' + currentBot, '-Pmaps=' + map]))
        else:
            print('You are running on an unsupported platform')
        
    except subprocess.CalledProcessError as exc:
        print("Status: FAIL", exc.returncode, exc.output)
        return 'Error'
    
    winAString = '{} (A) wins'.format(currentBot)
    winBString = '{} (B) wins'.format(currentBot)
    loseAString = '{} (B) wins'.format(bot)
    loseBString = '{} (A) wins'.format(bot)
    
    numWins = 0
    
    gameLengthA = retrieveGameLength(outputA)
    gameLengthB = retrieveGameLength(outputB)
    
    if winAString in outputA:
        numWins += 1
        toAddWins += 1
    else:
        if not loseAString in outputA:
            return 'Error'
    if winBString in outputB:
        numWins += 1
        toAddWins += 1
    else:
        if not loseBString in outputB:
            return 'Error'
    return numWinsMapping[numWins] + ' (' + ', '.join([gameLengthA, gameLengthB]) + ')', toAddWins


results = {}
# Run matches
print('Running {} maps'.format(len(matches)))
for bot, map in matches:
    # Verify match is valid
    if not bot in botsSet or not map in mapsSet:
        errors.append('Unable to parse bot={}, map={}'.format(bot, map))
    # run run_match.py
    
    results[(bot, map)], toAddWins = run_match(bot, map, 0)
    totalWins += toAddWins

# Construct table
table = [[results.get((bot, map), 'N/A') for bot in bots] for map in maps]
# Create Wins, Losses, WinRatio variables
Wins = totalWins
Losses = len(matches)*2 - Wins
if Wins + Losses == 0:
    WinRatio = 0
else:
    WinRatio = 100 * Wins / (Wins + Losses)
# Create string with Wins, Losses, and WinRatio
match_statistics = 'Win ratio: {}/{} ({:.2f}%)'.format(Wins, Wins + Losses, WinRatio) + '\n'

def replaceWithDictionary(s, mapping):
    for a, b in mapping.items():
        s = s.replace(a, b)
    return s

if emojiMode:
    table = [[replaceWithDictionary(item, emojiMap) for item in row] for row in table]

# Write to file
with open('matches-summary-test2.txt', 'w') as f:
    #Write the ratio of wins to total games, and win percentage
    f.write(match_statistics)

    table = [[''] + bots, [':---:' for i in range(len(bots) + 1)]] + [[map] + row for map, row in zip(maps, table)]
    for line in table:
        f.write('| ')
        f.write(' | '.join(line))
        f.write(' |')
        f.write('\n')
    f.write('\n')
    for error in errors:
        f.write(error)