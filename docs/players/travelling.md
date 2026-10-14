---
title: Travelling
parent: Player guide
nav_order: 2
---

# Travelling

## Discovering stops

You can only travel to stops you know. To learn a stop, **right-click its coach**: road dust kicks up at the wheels,
embers rise from the lanterns, the coachman sounds his horn, and a road-sign card says *Stop discovered* with the
stop's name. It's yours to travel to from then on.

You know your own coaches from the moment you place them. Some servers mark a few stops as public: everyone knows
those without finding them. With **Jade**, an undiscovered coach says *Not discovered - right-click to add it*.

## The destination list

Right-click a stop you know to open the list of every other stop you know, nearest first. Each line shows the stop's
name, how far it is, the fare and the travel time; hover over one for its coordinates. Your own stops are marked as
yours. Type in **Search by name** to narrow the list.

To set off, double-click a stop, or pick it and press **Travel** (or Enter).

A stop you can't travel to right now is greyed out, and hovering over it says why:

| It says | Why |
|---|---|
| Costs … - you don't have enough | You can't pay the fare |
| In another dimension | Coaches don't run between dimensions (unless your server allows it) |
| Too far for one trip - travel via a stop in between | Your server limits how far one journey goes: change coaches on the way |
| That stop is closed to you for now (or the server's own message) | The server has locked it behind something: an advancement, a level, an item… |

You need to stand within 12 blocks of the coach you're leaving from.

## The fare

You pay when you set off. By default a journey costs **1 level, plus 1 more for every 5,000 blocks or part of that, up
to 10 levels**:

| Distance | Fare |
|---|---|
| up to 5,000 blocks | 2 levels |
| 5,001 to 10,000 | 3 levels |
| 20,000 | 5 levels |
| 45,000 or more | 10 levels |

Some servers charge **items instead of levels**: emeralds, coins, any item at all. The list then shows the items'
cost, and how many you carry. Players in creative mode travel free. Some stops are free for everyone.

## The journey

A journey takes time: **75 blocks a second, at least 5 seconds and at most 10 minutes**. A trip of 1,500 blocks takes
20 seconds; 45,000 blocks or more takes the full 10 minutes.

While you travel, the road rolls past on the travel screen with your own figure walking along it, above a progress bar
and the time left. Meanwhile you wait out of sight, safe, where you set off. (You can switch the road for a map of the
route or a plain screen: see [Your options](options.md).)

You can't set off within 10 seconds of being hit. Your server may also make you rest a little after you arrive before
you travel again.

## Cancelling

Press **Esc** or **Cancel journey** at any time. *Abandon journey?* asks to make sure; choose **Abandon** and you're set
down at the coach you left from, with your fare back (some servers keep part of it, and the box says how much).

## Arriving

When the time is up you're set down on safe ground in front of the destination coach, in your own game mode, to the
sound of a bell.

## When a journey ends early

Your fare always comes back and you end up where you set off, except when you die:

| What happened | You get |
|---|---|
| You cancelled | Set down where you left from, fare back |
| The destination coach was taken down | Set down where you left from, fare back |
| You left the game | Back where you left from when you return, fare back |
| The server stopped or crashed | Back where you left from when you return, fare and game mode back |
| You died on the road | You respawn as usual, fare back |
| Your game mode was changed | The journey is called off, fare back |

Every journey is written to disk before anything about you changes, so nothing is lost even if the server crashes.
