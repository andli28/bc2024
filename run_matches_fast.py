from itertools import product
import subprocess

import platform
import concurrent.futures
import os

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

bots = ['v6']
botsSet = set(bots)

sprint1Maps = ['AceOfSpades', 'Alien', 'Ambush', 'Battlecode24', 'BigDucksBigPond', 'Canals', 'CH3353C4K3F4CT0RY',
    'Duck', 'Fountain', 'Hockey', 'MazeRunner', 'Rivers', 'Snake', 'Soccer', 'SteamboatMickey', 'Yinyang']
initialMaps = ['DefaultHuge', 'DefaultLarge', 'DefaultMedium', 'DefaultSmall']
customMaps = ['pathfinder', 'bridge_aquifer', 'buh', 'crashtest1', 'Maze', 'one_on_one', 'pathfinder2', 'two_moats',
              'kirby', 'diagonal_grid']

maps = initialMaps + sprint1Maps # + customMaps
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

def run_subprocess(teamA, teamB, map):
    if platform.system() == 'Windows':
        output = str(subprocess.check_output(['gradlew', 'run', '-PteamA=' + teamA, '-PteamB=' + teamB, '-Pmaps=' + map], shell=True, stderr=subprocess.DEVNULL))
    elif platform.system() == 'Linux':
        output = str(subprocess.check_output(['./gradlew', 'run', '-PteamA=' + teamA, '-PteamB=' + teamB, '-Pmaps=' + map], stderr=subprocess.DEVNULL))
    else:
        print('You are running on an unsupported platform')
    return output

def run_match(bot, map):
    print("Running {} vs {} on {}".format(currentBot, bot, map))
    try:
        outputA = run_subprocess(currentBot, bot, map)
        outputB = run_subprocess(bot, currentBot, map)
        
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
    else:
        if not loseAString in outputA:
            return 'Error'
    if winBString in outputB:
        numWins += 1
    else:
        if not loseBString in outputB:
            return 'Error'
    return numWinsMapping[numWins] + ' (' + ', '.join([gameLengthA, gameLengthB]) + ')', numWins


if __name__ == "__main__":
    results = {}
    # Show how many matches to play, along with how many cpus we have
    print('Running {} matches on {} cpus'.format(len(matches), os.cpu_count()))
    
    with concurrent.futures.ProcessPoolExecutor() as executor:
        future_to_game = {executor.submit(run_match, bot, map): (bot, map) for bot, map in matches}
        for future in concurrent.futures.as_completed(future_to_game):
            print('Finished number {} of {}'.format(len(results) + 1, len(matches)))
            bot, map = future_to_game[future]
            try:
                results[(bot, map)], toAddWins = future.result()
                totalWins += toAddWins
            except Exception as exc:
                print('%r generated an exception: %s' % (bot, exc))

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
    with open('matches-summary-fast.txt', 'w') as f:
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