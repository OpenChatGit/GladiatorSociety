#!/usr/bin/env python3
"""Static checks for Gladiator Society's packaged configuration data."""

import csv
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ERRORS = []
WARNINGS = []


def report(path, message, warning=False):
    target = WARNINGS if warning else ERRORS
    target.append(f"{path.relative_to(ROOT).as_posix()}: {message}")


def strip_json_comments(text):
    """Accept the // and # comments used by Starsector mod JSON files."""
    result = []
    quote = None
    escaped = False
    index = 0
    while index < len(text):
        char = text[index]
        if quote:
            result.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            index += 1
            continue
        if char in ('"', "'"):
            quote = char
            result.append(char)
            index += 1
            continue
        if char == "#" or text.startswith("//", index):
            newline = text.find("\n", index)
            if newline < 0:
                break
            result.append("\n")
            index = newline + 1
            continue
        result.append(char)
        index += 1
    return re.sub(r",\s*([}\]])", r"\1", "".join(result))


def read_relaxed_json(path):
    try:
        text = strip_json_comments(path.read_text(encoding="utf-8-sig"))
        # Starsector's custom_entities.json permits layer constants as bare tokens.
        if path.relative_to(ROOT).as_posix() == "data/config/custom_entities.json":
            text = re.sub(r"\b(STATIONS|BELOW_STATIONS)\b", r'"\1"', text)
        return json.loads(text)
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        report(path, f"invalid JSON: {error}")
        return None


def read_csv(path, expected_header):
    try:
        lines = [line for line in path.read_text(encoding="utf-8-sig").splitlines()
                 if line.strip() and not line.lstrip().startswith("#")]
        rows = list(csv.reader(lines, strict=True))
    except (OSError, UnicodeError, csv.Error) as error:
        report(path, f"invalid CSV: {error}")
        return []
    if not rows:
        report(path, "CSV is empty")
        return []
    header = [cell.strip() for cell in rows[0]]
    if len(header) != len(set(header)):
        report(path, "header contains duplicate column names")
    missing = set(expected_header) - set(header)
    if missing:
        report(path, "missing columns: " + ", ".join(sorted(missing)))
        return []
    result = []
    for line_number, values in enumerate(rows[1:], start=2):
        if len(values) != len(header):
            report(path, f"line {line_number}: expected {len(header)} columns, found {len(values)}")
            continue
        result.append((line_number, dict(zip(header, (value.strip() for value in values)))) )
    return result


def check_json_files():
    for path in sorted((ROOT / "data").rglob("*.json")):
        read_relaxed_json(path)

    mission_dir = ROOT / "data/config/gsounty"
    variant_dir = mission_dir / "gladiator_variants"
    for path in sorted(mission_dir.glob("*.json")):
        mission = read_relaxed_json(path)
        if not isinstance(mission, dict):
            report(path, "bounty root must be a JSON object")
            continue
        custom = mission.get("mainShipCustom")
        if custom is not None and (not isinstance(custom, list) or len(custom) < 2
                                   or not all(isinstance(value, str) and value.strip() for value in custom[:2])):
            report(path, "mainShipCustom must contain a non-empty variant ID and hull ID")
        elif custom is not None:
            check_custom_variant(path, variant_dir, custom[0])
        advships = mission.get("advships", [])
        if not isinstance(advships, list):
            report(path, "advships must be an array")
            continue
        for index, ship in enumerate(advships):
            if not isinstance(ship, list) or len(ship) < 2:
                report(path, f"advships[{index}] must contain a variant ID and ship count")
                continue
            if not isinstance(ship[0], str) or not ship[0].strip():
                report(path, f"advships[{index}] has an empty variant ID")
            if isinstance(ship[1], bool) or not isinstance(ship[1], int) or ship[1] <= 0:
                report(path, f"advships[{index}] ship count must be a positive integer")
            if len(ship) > 4:
                report(path, f"advships[{index}] has unsupported extra fields", warning=True)
            if len(ship) == 4 and isinstance(ship[3], str) and ship[3].strip():
                check_custom_variant(path, variant_dir, ship[0])
        avatar = mission.get("avatar", "")
        if isinstance(avatar, str) and avatar.startswith("graphics/portraits/GladiatorSociety_"):
            if not (ROOT / avatar).is_file():
                report(path, f"local avatar file does not exist: {avatar}")

    for path in sorted(variant_dir.rglob("*.variant")):
        variant = read_relaxed_json(path)
        if not isinstance(variant, dict):
            report(path, "custom variant root must be a JSON object")
            continue
        if not isinstance(variant.get("hullId"), str) or not variant["hullId"].strip():
            report(path, "custom variant requires a non-empty hullId")
        for key in ("hullMods", "permaMods", "wings", "weaponGroups", "modules"):
            if key in variant and not isinstance(variant[key], list):
                report(path, f"{key} must be an array")
        modules = variant.get("modules", [])
        if isinstance(modules, list):
            for index, module in enumerate(modules):
                if not isinstance(module, dict) or len(module) != 1:
                    report(path, f"modules[{index}] must map one module slot to one variant ID")
                else:
                    module_variant = next(iter(module.values()))
                    if not isinstance(module_variant, str) or not module_variant.strip():
                        report(path, f"modules[{index}] has an empty or invalid variant ID")


def check_custom_variant(bounty_path, variant_dir, variant_id):
    if not isinstance(variant_id, str) or not variant_id.strip():
        report(bounty_path, "custom variant ID is empty")
        return
    candidate = (variant_dir / (variant_id + ".variant")).resolve()
    try:
        candidate.relative_to(variant_dir.resolve())
    except ValueError:
        report(bounty_path, f"custom variant path escapes its data folder: {variant_id}")
        return
    if not candidate.is_file():
        report(bounty_path, f"custom variant file does not exist: {variant_id}.variant")


def check_csv_files():
    missions_path = ROOT / "data/config/gsounty/Missions.csv"
    mission_rows = read_csv(missions_path, ("missionid",))
    seen_missions = set()
    for line, row in mission_rows:
        mission_id = row["missionid"]
        if not mission_id:
            report(missions_path, f"line {line}: missionid is empty")
            continue
        if mission_id in seen_missions:
            report(missions_path, f"line {line}: duplicate missionid '{mission_id}'")
        seen_missions.add(mission_id)
        if not re.fullmatch(r"[A-Za-z0-9_-]+", mission_id):
            report(missions_path, f"line {line}: missionid contains unsupported characters")
        bounty = ROOT / "data/config/gsounty" / f"{mission_id}.json"
        if not bounty.is_file():
            report(missions_path, f"line {line}: bounty file is missing: {bounty.name}")

    faction_path = ROOT / "data/config/gsounty/EndlessFaction.csv"
    faction_rows = read_csv(faction_path, ("factionid",))
    seen_factions = set()
    for line, row in faction_rows:
        faction = row["factionid"]
        if not faction:
            report(faction_path, f"line {line}: factionid is empty")
        elif faction in seen_factions:
            report(faction_path, f"line {line}: duplicate factionid '{faction}'")
        seen_factions.add(faction)

    rewards_path = ROOT / "data/config/gsounty/EndlessReward.csv"
    rewards = read_csv(rewards_path, ("id_reward", "id_resource", "number", "roundReward"))
    seen_rewards = set()
    for line, row in rewards:
        reward_id = row["id_reward"]
        if not reward_id or not row["id_resource"]:
            report(rewards_path, f"line {line}: id_reward and id_resource are required")
        elif reward_id in seen_rewards:
            report(rewards_path, f"line {line}: duplicate id_reward '{reward_id}'")
        seen_rewards.add(reward_id)
        for column in ("number", "roundReward"):
            value = row[column]
            if value:
                try:
                    if int(value) < 0:
                        raise ValueError
                except ValueError:
                    report(rewards_path, f"line {line}: {column} must be a non-negative integer")

    blueprint_path = ROOT / "data/config/gsounty/BlueprintRewards.csv"
    blueprints = read_csv(blueprint_path, ("item_id", "blueprint_type", "min_credit_value",
                                            "max_credit_value", "weight", "name"))
    seen_blueprints = set()
    for line, row in blueprints:
        item_id = row["item_id"]
        if not item_id:
            report(blueprint_path, f"line {line}: item_id is required")
        elif item_id in seen_blueprints:
            report(blueprint_path, f"line {line}: duplicate item_id '{item_id}'")
        seen_blueprints.add(item_id)
        for column in ("min_credit_value", "max_credit_value", "weight"):
            try:
                value = int(row[column])
                if value < 0 or (column == "weight" and value == 0):
                    raise ValueError
            except ValueError:
                report(blueprint_path, f"line {line}: {column} must be a positive/non-negative integer")
        try:
            if int(row["max_credit_value"]) < int(row["min_credit_value"]):
                report(blueprint_path, f"line {line}: max_credit_value is below min_credit_value")
        except ValueError:
            pass  # The invalid numeric value is already reported above.

    conditions_path = ROOT / "data/campaign/market_conditions.csv"
    conditions = read_csv(conditions_path, ("id", "planetary", "script", "icon"))
    seen_conditions = set()
    for line, row in conditions:
        condition_id = row["id"]
        if not condition_id:
            report(conditions_path, f"line {line}: id is required")
        elif condition_id in seen_conditions:
            report(conditions_path, f"line {line}: duplicate condition id '{condition_id}'")
        seen_conditions.add(condition_id)
        plugin = row["script"].replace(".", "/") + ".java"
        if row["script"] and not (ROOT / plugin).is_file():
            report(conditions_path, f"line {line}: condition plugin source does not exist: {row['script']}")


def main():
    check_json_files()
    check_csv_files()
    for warning in WARNINGS:
        print(f"::warning::{warning}")
    for error in ERRORS:
        print(f"::error::{error}")
    if ERRORS:
        print(f"Mod data validation failed: {len(ERRORS)} error(s), {len(WARNINGS)} warning(s)")
        return 1
    print(f"Mod data validation passed: {len(WARNINGS)} warning(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
